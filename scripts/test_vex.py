import json
import subprocess
import sys
import tempfile
import tomllib
import unittest
from datetime import UTC, date, datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import vex  # noqa: E402

# Os dois aceites reais da Feira do Comando, copiados do osv-scanner.toml de lá.
ACEITES = tomllib.loads("""
[[IgnoredVulns]]
id = "GHSA-j9f9-w8pj-32f8"
ignoreUntil = 2027-01-28
reason = "CVE-2026-47890: exige SSE com fragmentos de view; o projeto não usa SSE nem view."

[[IgnoredVulns]]
id = "GHSA-pc63-qcmh-9cmg"
ignoreUntil = 2027-01-28
reason = "CVE-2026-47884: exige XsltView; o projeto não renderiza view nenhuma."
""")["IgnoredVulns"]

PRODUTO = "pkg:github/fabriciojunio/feira-do-comando"


class GeradorDeVexTest(unittest.TestCase):
    def test_aceite_no_prazo_vira_nao_afetado_com_justificativa_e_cve(self):
        declaracao = vex.declaracoes(ACEITES, PRODUTO, date(2026, 10, 29), {})[0]
        self.assertEqual(declaracao["status"], "not_affected")
        self.assertEqual(declaracao["justification"], "vulnerable_code_not_in_execute_path")
        self.assertEqual(declaracao["vulnerability"], {"name": "GHSA-j9f9-w8pj-32f8", "aliases": ["CVE-2026-47890"]})
        self.assertIn("Reavaliação até 2027-01-28", declaracao["impact_statement"])

    def test_aceite_vencido_nao_afirma_que_nao_afeta(self):
        declaracao = vex.declaracoes(ACEITES, PRODUTO, date(2027, 2, 1), {})[0]
        self.assertEqual(declaracao["status"], "under_investigation")
        self.assertNotIn("justification", declaracao)

    def test_justificativa_pode_ser_trocada_por_vulnerabilidade(self):
        mapa = {"GHSA-pc63-qcmh-9cmg": "component_not_present"}
        segunda = vex.declaracoes(ACEITES, PRODUTO, date(2026, 10, 29), mapa)[1]
        self.assertEqual(segunda["justification"], "component_not_present")

    def test_justificativa_fora_da_especificacao_e_recusada(self):
        with self.assertRaises(SystemExit):
            vex.ler_justificativas(["GHSA-x=porque_sim"])

    def test_documento_segue_o_formato_openvex(self):
        doc = vex.documento(ACEITES, PRODUTO, "Fabrício Júnio", datetime(2026, 10, 29, tzinfo=UTC), {})
        self.assertEqual(doc["@context"], "https://openvex.dev/ns/v0.2.0")
        self.assertEqual(len(doc["statements"]), 2)
        self.assertTrue(all(s["products"] == [{"@id": PRODUTO}] for s in doc["statements"]))

    def test_linha_de_comando_gera_o_arquivo(self):
        with tempfile.TemporaryDirectory() as pasta:
            config = Path(pasta) / "osv-scanner.toml"
            aceite = '[[IgnoredVulns]]\nid = "GHSA-j9f9-w8pj-32f8"\nignoreUntil = 2099-01-01\n'
            config.write_text(aceite + 'reason = "CVE-2026-47890: x"\n', encoding="utf-8")
            saida = Path(pasta) / "vex.json"
            subprocess.run(
                [sys.executable, str(Path(__file__).parent / "vex.py"), str(config), PRODUTO, str(saida)],
                check=True,
                capture_output=True,
            )
            self.assertEqual(json.loads(saida.read_text(encoding="utf-8"))["statements"][0]["status"], "not_affected")


if __name__ == "__main__":
    unittest.main()
