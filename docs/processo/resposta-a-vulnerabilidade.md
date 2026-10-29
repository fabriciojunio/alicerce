# Resposta a vulnerabilidade reportada

| | |
|---|---|
| Dono do processo | Segurança de aplicações |
| Revisão | 29/10/2026 |
| Base | ISO/IEC 29147 (divulgação) e ISO/IEC 30111 (tratamento) |

Este é o caminho de uma falha que alguém de fora nos conta, do recebimento até a versão corrigida e o
aviso público. Falha achada pela própria equipe segue a
[gestão de vulnerabilidades](gestao-de-vulnerabilidades.md).

## Prazos com quem reportou

| Etapa | Prazo |
|---|---|
| Confirmar o recebimento | 3 dias úteis |
| Dar a avaliação inicial (aceito, precisa de dado, não é falha) | 10 dias úteis |
| Corrigir e publicar | conforme a prioridade (P1 em 7 dias) |
| Divulgação coordenada | até 90 dias do relato, ou antes se a correção sair |

## Passo a passo

1. **Receber.** Pelo aviso privado do GitHub ou pelo e-mail de `SECURITY.md`. Abrir o registro
   privado (GitHub Security Advisory em rascunho) e confirmar o recebimento.
2. **Reproduzir.** Em ambiente isolado, nunca em produção com dado real. Sem reprodução, pedir mais
   detalhe a quem reportou antes de descartar.
3. **Avaliar.** CVSS 4.0 com o vetor, versões afetadas, se há exploração conhecida, e a prioridade
   pela tabela da gestão de vulnerabilidades.
4. **Corrigir em privado.** Branch no fork privado do advisory, com teste que reproduz a falha e
   passa a barrá-la. Revisão por outra pessoa.
5. **Pedir o CVE.** Pelo próprio advisory do GitHub, que é CNA e atribui o identificador.
6. **Lançar.** Versão nova com a correção; o aviso público sai junto, no
   [modelo de advisory](modelo-de-advisory.md).
7. **Agradecer.** Crédito a quem reportou, se a pessoa quiser.
8. **Revisar.** O mesmo erro existe em outro serviço? A regra de SAST pegaria? Se não, escrever a
   regra.

## Dependência de terceiro com CVE

Quando o CVE é de uma biblioteca que distribuímos, o caminho é o mesmo a partir do passo 3, com dois
cuidados: conferir se o trecho vulnerável é alcançável no nosso uso (o `govulncheck` e o OSV ajudam)
e, se não for, registrar isso no aviso e no aceite, em vez de só dizer "não afetado".
