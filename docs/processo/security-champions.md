# Programa de Security Champions

| | |
|---|---|
| Dono do programa | Segurança de aplicações |
| Revisão | 29/10/2026 |
| Base | OWASP Security Champions Guide |

## Para que serve

O time de segurança não cabe em toda reunião de toda squad. O champion é uma pessoa da própria squad
que leva a pergunta de segurança para o refinamento e o code review, e traz para o time de segurança
o que a squad está construindo antes de ir para produção.

O champion não é o responsável pela segurança da squad. A squad continua dona do que entrega; o
champion encurta o caminho até quem pode ajudar.

## Quem pode ser

Alguém da squad, de qualquer nível, que se interesse pelo assunto e tenha o aval do líder. Um por
squad; dois em squad grande.

## Tempo

Cerca de 10% da semana, combinado com o líder e contado na capacidade da squad. Sem esse acordo o
papel vira trabalho extra e acaba em três meses.

## O que o champion faz

| Quando | O quê |
|---|---|
| Refinamento | Pergunta "quem pode chamar isso, e o que acontece se for outra pessoa?" nas histórias que mexem com autenticação, dado pessoal, pagamento ou integração externa |
| Feature nova relevante | Faz o modelo de ameaças com a squad, usando o [modelo](../seguranca/modelo-de-ameacas.md) deste repositório |
| Code review | Revisa com a lista curta abaixo |
| Achado de ferramenta | Faz a primeira triagem na squad e chama segurança quando for P1 ou P2 |
| Mensal | Participa da guilda de champions (1 hora) |

Lista curta de revisão:

1. O dono do recurso sai do token, e não da URL ou do corpo?
2. Toda entrada tem validação no servidor?
3. Alguma resposta devolve campo que o cliente não deveria ver?
4. Algum log grava dado pessoal, token ou senha?
5. Dependência nova: tem mais de 7 dias de publicada e passou no OSV?

## Trilha de formação

| Etapa | Conteúdo | Gratuito |
|---|---|---|
| 1 | OWASP Top 10 e OWASP API Security Top 10 | sim |
| 2 | PortSwigger Web Security Academy: controle de acesso e autenticação | sim |
| 3 | Este repositório: ler a configuração de segurança e os testes | sim |
| 4 | Modelagem de ameaças com STRIDE, numa feature real da squad | sim |
| 5 | Ler o relatório do [brecha](https://github.com/fabriciojunio/brecha) e refazer duas explorações | sim |

## Guilda mensal

Uma hora, sempre no mesmo dia. Um caso real do mês (falha encontrada ou evitada), uma técnica nova e
dúvidas. A ata fica registrada, sem detalhe explorável de falha aberta.

## Como saber se está funcionando

- Quantas features relevantes tiveram modelo de ameaças antes do desenvolvimento.
- Quantos achados P1 e P2 a squad encontrou antes da produção, e não depois.
- Tempo médio de correção da squad, comparado com antes do programa.

Reconhecimento conta: o trabalho do champion entra na avaliação de desempenho da pessoa.
