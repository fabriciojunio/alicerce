import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import openscap_resumo as resumo  # noqa: E402


def resultado(nota: float, regras: dict[str, str], arquivo_arf: bool = False) -> str:
    itens = "".join(
        f'<rule-result idref="xccdf_org.ssgproject.content_rule_{r}"><result>{v}</result></rule-result>'
        for r, v in regras.items()
    )
    teste = (
        '<TestResult xmlns="http://checklists.nist.gov/xccdf/1.2" id="t">'
        f'{itens}<score system="urn:xccdf:scoring:default">{nota}</score></TestResult>'
    )
    if arquivo_arf:
        # O --results do oscap grava o Benchmark com o TestResult dentro.
        return f'<Benchmark xmlns="http://checklists.nist.gov/xccdf/1.2">{teste}</Benchmark>'
    return teste


class ResumoTest(unittest.TestCase):
    def setUp(self) -> None:
        self.pasta = Path(tempfile.mkdtemp())

    def gravar(self, nome: str, conteudo: str) -> Path:
        caminho = self.pasta / nome
        caminho.write_text(conteudo, encoding="utf-8")
        return caminho

    def test_le_nota_e_resultado_de_cada_regra(self) -> None:
        caminho = self.gravar("r.xml", resultado(61.5, {"sshd_disable_root_login": "fail"}, arquivo_arf=True))
        medicao = resumo.ler(caminho)
        self.assertEqual(medicao.nota, 61.5)
        self.assertEqual(medicao.resultados, {"sshd_disable_root_login": "fail"})

    def test_arquivo_sem_resultado_e_recusado(self) -> None:
        caminho = self.gravar("vazio.xml", '<Benchmark xmlns="http://checklists.nist.gov/xccdf/1.2"/>')
        with self.assertRaisesRegex(ValueError, "TestResult"):
            resumo.ler(caminho)

    def test_controles_ignoram_comentario_e_linha_em_branco(self) -> None:
        caminho = self.gravar("c.txt", "# SSH\nsshd_disable_root_login  # PermitRootLogin no\n\nsysctl_fs_suid_dumpable\n")
        self.assertEqual(resumo.ler_controles(caminho), ["sshd_disable_root_login", "sysctl_fs_suid_dumpable"])

    def test_controle_que_some_do_resultado_conta_como_pendente(self) -> None:
        depois = resumo.Medicao(80.0, {"a": "pass"})
        self.assertEqual(resumo.pendentes(depois, ["a", "b"]), [("b", "ausente")])

    def test_portao_reprova_quando_um_controle_falha(self) -> None:
        antes = self.gravar("antes.xml", resultado(50, {"a": "fail", "b": "fail"}))
        depois = self.gravar("depois.xml", resultado(70, {"a": "pass", "b": "fail"}))
        controles = self.gravar("c.txt", "a\nb\n")
        self.assertEqual(resumo.principal([str(antes), str(depois), str(controles)]), 1)

    def test_portao_aprova_e_grava_a_tabela(self) -> None:
        antes = self.gravar("antes.xml", resultado(50, {"a": "fail", "b": "fail", "c": "fail"}))
        depois = self.gravar("depois.xml", resultado(70, {"a": "pass", "b": "pass", "c": "fail"}))
        controles = self.gravar("c.txt", "a\nb\n")
        tabela = self.pasta / "resumo.md"

        self.assertEqual(resumo.principal([str(antes), str(depois), str(controles), "--resumo", str(tabela)]), 0)
        texto = tabela.read_text(encoding="utf-8")
        self.assertIn("| Nota do perfil | 50.0 | 70.0 |", texto)
        self.assertIn("| Controles do projeto aprovados | 0 de 2 | 2 de 2 |", texto)
        self.assertNotIn("não passaram", texto)


if __name__ == "__main__":
    unittest.main()
