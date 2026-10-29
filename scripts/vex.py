"""Gera um documento OpenVEX a partir dos aceites do osv-scanner.toml.

Cada vulnerabilidade aceita no OSV já tem motivo e prazo escritos. O VEX publica a mesma decisão num
formato que o scanner do cliente lê: quem baixa a imagem e acha a CVE no SBOM fica sabendo que ela
não se aplica, e por quê, sem abrir chamado.

Aceite dentro do prazo vira `not_affected`. Aceite vencido vira `under_investigation`: passado o
prazo, ninguém reavaliou, e afirmar que não afeta seria mentir.

Uso: python scripts/vex.py <osv-scanner.toml> <id do produto, ex. pkg:oci/feira@sha256:...> <saida.json>
     [--autor "Nome"] [--justificativa GHSA-xxxx=component_not_present ...]
"""

import argparse
import json
import re
import sys
import tomllib
from datetime import UTC, date, datetime
from pathlib import Path

JUSTIFICATIVAS = {
    "component_not_present",
    "vulnerable_code_not_present",
    "vulnerable_code_not_in_execute_path",
    "vulnerable_code_cannot_be_controlled_by_adversary",
    "inline_mitigations_already_exist",
}
PADRAO = "vulnerable_code_not_in_execute_path"
CVE = re.compile(r"CVE-\d{4}-\d{4,}")


def declaracoes(aceites: list[dict], produto: str, hoje: date, justificativas: dict[str, str]) -> list[dict]:
    resultado = []
    for aceite in aceites:
        vuln = {"name": aceite["id"]}
        apelidos = sorted(set(CVE.findall(aceite.get("reason", ""))))
        if apelidos:
            vuln["aliases"] = apelidos
        prazo = aceite.get("ignoreUntil")
        prazo_data = prazo.date() if isinstance(prazo, datetime) else prazo
        declaracao = {"vulnerability": vuln, "products": [{"@id": produto}]}
        if prazo_data is not None and prazo_data < hoje:
            declaracao["status"] = "under_investigation"
            declaracao["status_notes"] = f"aceite venceu em {prazo_data.isoformat()} e aguarda reavaliação"
        else:
            declaracao["status"] = "not_affected"
            declaracao["justification"] = justificativas.get(aceite["id"], PADRAO)
            nota = f" Reavaliação até {prazo_data.isoformat()}." if prazo_data else ""
            declaracao["impact_statement"] = aceite.get("reason", "").strip() + nota
        resultado.append(declaracao)
    return resultado


def documento(aceites: list[dict], produto: str, autor: str, agora: datetime, justificativas: dict[str, str]) -> dict:
    return {
        "@context": "https://openvex.dev/ns/v0.2.0",
        "@id": f"{produto}#vex-{agora:%Y%m%d}",
        "author": autor,
        "timestamp": agora.isoformat(timespec="seconds"),
        "version": 1,
        "statements": declaracoes(aceites, produto, agora.date(), justificativas),
    }


def ler_justificativas(pares: list[str]) -> dict[str, str]:
    mapa = {}
    for par in pares:
        vuln, _, codigo = par.partition("=")
        if codigo not in JUSTIFICATIVAS:
            raise SystemExit(f"justificativa inválida para {vuln}: {codigo}")
        mapa[vuln] = codigo
    return mapa


def main() -> int:
    argumentos = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    argumentos.add_argument("config")
    argumentos.add_argument("produto")
    argumentos.add_argument("saida")
    argumentos.add_argument("--autor", default="Fabrício Júnio")
    argumentos.add_argument("--justificativa", action="append", default=[])
    lido = argumentos.parse_args()
    aceites = tomllib.loads(Path(lido.config).read_text(encoding="utf-8")).get("IgnoredVulns", [])
    vex = documento(aceites, lido.produto, lido.autor, datetime.now(UTC), ler_justificativas(lido.justificativa))
    Path(lido.saida).write_text(json.dumps(vex, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{len(vex['statements'])} declarações em {lido.saida}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
