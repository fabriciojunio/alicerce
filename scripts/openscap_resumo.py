"""Resume o resultado do OpenSCAP e confere os controles que o projeto se comprometeu a cumprir.

O perfil CIS tem centenas de regras, e nem todas cabem num runner ou num host de contêiner. Por isso o
portão não exige o perfil inteiro: exige a lista de `infra/host/controles.txt`, que é o compromisso
explícito do projeto. Regra da lista que não passa no "depois" reprova; o resto entra só na contagem.

Uso: python scripts/openscap_resumo.py <antes.xml> <depois.xml> <controles.txt> [--resumo arquivo.md]
"""

import argparse
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

XCCDF = "{http://checklists.nist.gov/xccdf/1.2}"
PREFIXO_REGRA = "xccdf_org.ssgproject.content_rule_"


@dataclass(frozen=True)
class Medicao:
    nota: float
    resultados: dict[str, str]

    def contagem(self) -> Counter[str]:
        return Counter(self.resultados.values())


def ler(caminho: Path) -> Medicao:
    """Lê o TestResult de um arquivo de resultados do `oscap xccdf eval --results`."""
    raiz = ET.parse(caminho).getroot()
    teste = raiz if raiz.tag == f"{XCCDF}TestResult" else raiz.find(f".//{XCCDF}TestResult")
    if teste is None:
        raise ValueError(f"{caminho}: não há TestResult; o arquivo é do oscap xccdf eval --results?")
    resultados = {}
    for item in teste.iter(f"{XCCDF}rule-result"):
        regra = item.get("idref", "").removeprefix(PREFIXO_REGRA)
        resultado = item.find(f"{XCCDF}result")
        if regra and resultado is not None and resultado.text:
            resultados[regra] = resultado.text.strip()
    nota = teste.find(f"{XCCDF}score")
    return Medicao(float(nota.text) if nota is not None and nota.text else 0.0, resultados)


def ler_controles(caminho: Path) -> list[str]:
    """Uma regra por linha; linha em branco e comentário (#) são ignorados."""
    controles = []
    for linha in caminho.read_text(encoding="utf-8").splitlines():
        regra = linha.split("#", 1)[0].strip()
        if regra:
            controles.append(regra)
    return controles


def pendentes(depois: Medicao, controles: list[str]) -> list[tuple[str, str]]:
    """Controles que não passaram. Regra que nem foi avaliada também conta: sumir não é passar."""
    return [(c, depois.resultados.get(c, "ausente")) for c in controles if depois.resultados.get(c) != "pass"]


def resumo(antes: Medicao, depois: Medicao, controles: list[str]) -> str:
    ca, cd = antes.contagem(), depois.contagem()
    passaram_antes = sum(1 for c in controles if antes.resultados.get(c) == "pass")
    passaram_depois = sum(1 for c in controles if depois.resultados.get(c) == "pass")
    linhas = [
        "| | Antes | Depois |",
        "|---|---|---|",
        f"| Nota do perfil | {antes.nota:.1f} | {depois.nota:.1f} |",
        f"| Regras aprovadas | {ca['pass']} | {cd['pass']} |",
        f"| Regras reprovadas | {ca['fail']} | {cd['fail']} |",
        f"| Controles do projeto aprovados | {passaram_antes} de {len(controles)} | "
        f"{passaram_depois} de {len(controles)} |",
    ]
    falta = pendentes(depois, controles)
    if falta:
        linhas += ["", "Controles do projeto que não passaram:", ""]
        linhas += [f"- `{regra}`: {resultado}" for regra, resultado in falta]
    return "\n".join(linhas) + "\n"


def principal(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n", 1)[0])
    parser.add_argument("antes", type=Path)
    parser.add_argument("depois", type=Path)
    parser.add_argument("controles", type=Path)
    parser.add_argument("--resumo", type=Path, help="grava também a tabela neste arquivo")
    args = parser.parse_args(argv)

    antes, depois = ler(args.antes), ler(args.depois)
    controles = ler_controles(args.controles)
    texto = resumo(antes, depois, controles)
    print(texto)
    if args.resumo:
        with args.resumo.open("a", encoding="utf-8") as arquivo:
            arquivo.write(texto)
    return 1 if pendentes(depois, controles) else 0


if __name__ == "__main__":
    sys.exit(principal(sys.argv[1:]))
