# Hardening do host

O contêiner do serviço já sai endurecido (distroless, usuário sem privilégio, sistema de arquivos
somente leitura). Isso não protege o Linux onde ele roda. Esta parte cobre o host, medido pelo perfil
**CIS nível 1 para servidor do Ubuntu 24.04**, com o OpenSCAP e o conteúdo oficial do
ComplianceAsCode (0.1.82).

## Como funciona

O workflow `hardening.yml` roda num Ubuntu 24.04 limpo (o próprio runner, uma VM descartável):

1. mede o host com o OpenSCAP (antes);
2. aplica `infra/host/endurecer.yml`, um playbook Ansible curto, uma área por arquivo em
   `infra/host/tarefas/`;
3. mede de novo (depois);
4. `scripts/openscap_resumo.py` compara os dois e **reprova** se qualquer regra de
   `infra/host/controles.txt` não passar.

O perfil inteiro tem 408 regras. O portão não exige todas: exige as 74 que este projeto se
comprometeu a cumprir, listadas e comentadas em `controles.txt`. O resto entra só na contagem.

## Resultado medido

Execução do CI em 05/11/2026, no commit `3dd7a93`:

| | Antes | Depois |
|---|---|---|
| Nota do perfil | 67,5 | 74,0 |
| Regras aprovadas | 223 | 289 |
| Regras reprovadas | 121 | 67 |
| Controles do projeto aprovados | 10 de 74 | 74 de 74 |

Os relatórios HTML de antes e depois ficam como artefato de cada execução (`openscap-cis`).

## O que foi endurecido

| Área | Arquivo | Seção do CIS | Regras |
|---|---|---|---|
| SSH: sem root, sem senha vazia, cifras, MACs e troca de chave fortes, tempo de sessão, permissão das chaves | `ssh.yml` | 5.1 | 20 |
| Kernel e rede: sem redirecionamento nem rota imposta, filtro de origem, SYN cookies, ASLR, sem core dump de setuid | `kernel.yml` | 1.5 e 3.3 | 24 |
| Módulos de sistema de arquivos sem uso e armazenamento USB | `modulos.yml` | 1.1.1 | 6 |
| Cron restrito ao root | `cron.yml` | 2.4 | 7 |
| Qualidade e validade de senha, máscara de criação de arquivo | `contas.yml` | 5.3 e 5.4 | 14 |
| Aviso de uso autorizado, sem expor o sistema | `aviso.yml` | 1.7 | 3 |

## O que aprendemos no caminho

Cada um destes virou commit com a correção:

- **Configuração ambígua reprova mesmo com o valor certo.** O `rp_filter` e o `log_martians` já
  estavam certos em execução, mas outro arquivo definia o contrário: o `10-network-security.conf` do
  Ubuntu põe `rp_filter = 2`, e o `sysctl.conf` do ufw põe `log_martians=0`, escrito com barra
  (`net/ipv4/conf/all/log_martians`). A definição conflitante agora é comentada, com a indicação de
  quem a substituiu.
- **Arquivo vizinho também conta.** A permissão do `sshd_config.d` reprovou por causa do arquivo do
  cloud-init na mesma pasta, legível por todos.
- **Validar antes de recarregar evitou um SSH quebrado.** O `sshd -t` parou o playbook na primeira
  execução: com o SSH ativado por socket, a pasta `/run/sshd` ainda não existia.

## O que ficou de fora, e por quê

As 67 regras que seguem reprovadas no perfil inteiro, por área:

| Área | Regras | Por que não entra no compromisso agora |
|---|---|---|
| PAM e bloqueio de conta (faillock, histórico) | 11 | mexe na pilha de autenticação do runner; entra quando houver um host de verdade para testar o acesso de emergência |
| Firewall (ufw e nftables) | 10 | no runner disputa as regras com o Docker; no host real o firewall fica na borda da nuvem |
| Pacotes (instalar ou remover) | 8 | dependem da imagem base do provedor |
| Contas, permissões e outros | 16 | arquivos de inicialização do usuário, `PATH` do root, `at.allow`, expiração de conta |
| Serviços, AppArmor, GRUB, AIDE, partição e montagem | 15 | exigem reinício, partição separada ou senha de boot, que uma VM descartável não tem |
| Kernel | 2 | `ip_forward` e o equivalente do IPv6: o host roda contêiner e o Docker precisa encaminhar pacote |
| SSH | 1 | `AllowGroups` depende de quem administra o host |
| Logs, hora e sudo | 4 | configuração do provedor de nuvem |

## Para rodar num host de verdade

```bash
pipx install ansible-core==2.21.5
ansible-playbook -i servidor, infra/host/endurecer.yml --become
```

Antes, garanta acesso por chave SSH a um usuário que não seja o root: o playbook desliga o login do
root e o login sem senha.
