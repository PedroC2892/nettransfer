# NetTransfer — Análise Técnica de Rede, Robustez e Segurança

Data: 04/09/2026
Versão analisada: commit `cc23cfa`
Âmbito: comportamento com múltiplas interfaces de rede, gestão de espaço em disco,
e avaliação da encriptação e segurança da aplicação.

---

## Índice

1. [Múltiplas interfaces de rede](#1-múltiplas-interfaces-de-rede)
2. [Espaço em disco insuficiente](#2-espaço-em-disco-insuficiente)
3. [Análise de segurança](#3-análise-de-segurança)
4. [Avaliação global](#4-avaliação-global)
5. [Prioridades de correção](#5-prioridades-de-correção)

---

## 1. Múltiplas interfaces de rede

### 1.1 O que o código faz hoje

Em `DiscoveryService.broadcastDiscovery()`:

```java
InetAddress broadcastAddr = InetAddress.getByName("255.255.255.255");
DatagramSocket socket = new DatagramSocket();   // não faz bind a interface nenhuma
socket.send(packet);
```

`255.255.255.255` é o **limited broadcast**. Não é roteável e o kernel entrega-o a
**uma só interface** — tipicamente a da rota default. Não existe bind explícito,
portanto o sistema operativo decide sozinho por onde sai o pacote.

### 1.2 Comportamento com duas NICs

Cenário: `eth0` em 192.168.1.0/24 (rota default) e `wlan0` em 10.0.0.0/24.

| Situação | Resultado |
|---|---|
| Envio de broadcast | Sai **só** por `eth0`. Peers na rede 10.0.0.x nunca veem este dispositivo. |
| Receção de broadcast | O `DatagramSocket(54321)` faz bind a `0.0.0.0`, portanto **recebe de ambas**. |
| Efeito líquido | Descoberta **assimétrica**: este dispositivo vê o peer da wlan0, mas o peer não o vê a ele. |

Isto é problemático: um dos lados mostra o dispositivo na lista e o outro não.
Como o IP registado vem de `receivedPacket.getAddress()`, o card mostra o IP de origem
correto — mas a ligação TCP de volta pode sair pela interface errada se as rotas
forem ambíguas.

**Problema secundário:** o `Peer` é indexado por `id` (UUID), único por *instância*
e não por interface. Se um dia forem enviados broadcasts por ambas as interfaces,
o mesmo peer aparece uma única vez (correto), mas o campo `ipAddress` é sobrescrito
pelo último pacote recebido, escolhendo a rota de forma efetivamente aleatória.

### 1.3 Correção proposta

Enumerar interfaces e enviar um broadcast por cada uma, usando o endereço de broadcast
**específico da subnet** (ex.: `192.168.1.255`) em vez de `255.255.255.255`:

```java
for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
    if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
    for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
        InetAddress bcast = ia.getBroadcast();   // null para IPv6
        if (bcast == null) continue;
        socket.send(new DatagramPacket(data, data.length, bcast, port));
    }
}
```

Isto resolve a assimetria de descoberta.

**Alternativa mais robusta:** multicast (`239.x.x.x` com `joinGroup` por interface) —
é a abordagem usada pelo LocalSend — mas implica uma mudança de protocolo maior.

---

## 2. Espaço em disco insuficiente

### 2.1 O que acontece hoje

**Nada é verificado.** O `handleIncoming` cria a pasta e começa a escrever:

```java
Files.createDirectories(destBase);
try (FileOutputStream fos = new FileOutputStream(target.toFile())) {
    fos.write(buffer, 0, read);   // ← falha aqui quando o disco enche
}
```

### 2.2 Sequência de falha real

1. O recetor aceita uma transferência de 50 GB tendo apenas 10 GB livres
2. Escreve normalmente até aos ~10 GB
3. `fos.write()` lança `IOException: No space left on device`
4. O `catch` genérico apanha, regista `RECEIVE ERROR`, marca `TransferStatus.ERROR`,
   fecha o socket
5. O emissor vê a ligação cair a meio → `SocketException` → `SEND ERROR`

### 2.3 Avaliação

**Pontos positivos:** não corrompe nem faz crash da aplicação. Desde a última alteração
o log regista a razão exata (`IOException: No space left on device`) e quantos bytes
foram transferidos antes da falha.

**Problemas:**

- Os ~10 GB de ficheiros parciais **ficam no disco**, na pasta com timestamp.
  Nenhum mecanismo os limpa.
- O utilizador só descobre o problema depois de esperar pela transferência quase toda.
- O disco fica cheio, o que pode afetar o funcionamento do resto do sistema.

### 2.4 Correção proposta

Verificar antes de aceitar e mostrar a informação no diálogo de confirmação:

```java
long usable = Files.getFileStore(DOWNLOAD_BASE).getUsableSpace();
long needed = request.totalSize;
if (needed > usable) {
    // recusar automaticamente, ou avisar no diálogo:
    // "Not enough space: needs 50.0 GB, 10.2 GB available"
}
```

Complementarmente:

- Limpar os ficheiros parciais no bloco `catch`, apagando a pasta da transferência
  falhada se ficou incompleta.
- Usar uma margem de segurança (ex.: exigir `totalSize + 100 MB`) para evitar encher
  o disco até ao último byte.

---

## 3. Análise de segurança

### 3.1 O que está bem feito

#### ECDH efémero P-256

Cada ligação gera um par de chaves novo, usado uma única vez e descartado. Isto
proporciona **forward secrecy** genuína: capturar tráfego hoje e comprometer a máquina
amanhã não permite decifrar o que foi capturado, porque a chave privada já não existe
em lado nenhum.

#### AES-256-GCM

Cifra autenticada (AEAD) — protege confidencialidade *e* integridade. Um atacante que
altere um bit no ciphertext faz a tag de 128 bits falhar e o `doFinal()` lança exceção.
Não há forma de modificar dados em trânsito sem detecção.

#### IV aleatório por record

12 bytes de `SecureRandom` por cada bloco de 64 KB. Isto é importante: reutilizar um IV
em GCM com a mesma chave é catastrófico (permite recuperar a keystream e forjar
mensagens). Aqui está correto.

#### Curva e primitivas

secp256r1 e SHA-256 são padrões sólidos e bem suportados. Nada de exótico ou caseiro
na criptografia em si.

#### Path traversal bloqueado

O `resolveSafePath` normaliza e verifica `startsWith(base)` — um ficheiro chamado
`../../../.bashrc` é rejeitado. Está bem feito e é uma vulnerabilidade clássica que
muitas implementações falham.

---

### 3.2 Onde está fraco

#### FALHA CRÍTICA: sem autenticação — MITM total

Este é o problema mais grave. O handshake é ECDH **anónimo**: as chaves públicas são
trocadas sem qualquer verificação de identidade.

```
Tu  ←──[ECDH]──→  Atacante  ←──[ECDH]──→  Peer real
```

O atacante estabelece duas sessões cifradas separadas, decifra tudo no meio, e
reencaminha. Ambos os lados veem "ligação cifrada" e nada parece errado. A cifra está
a funcionar perfeitamente — só que com a pessoa errada.

Isto **não é** uma falha teórica numa LAN. ARP spoofing numa rede partilhada (café,
escritório, universidade, Wi-Fi de hotel) é trivial com ferramentas prontas. Além disso,
o próprio protocolo de descoberta é UDP broadcast sem assinatura — qualquer um pode
anunciar-se com o nome "Desktop-do-Pedro".

**Como resolver:** mostrar um **número de verificação** derivado das duas chaves
públicas (`SHA-256(pubA || pubB)` truncado a 6 dígitos) em ambos os ecrãs. Se os
números baterem certo, não há homem no meio. É essencialmente uma linha de UI e
resolve o problema quase todo. É a abordagem usada pelo Signal e pelo LocalSend.

#### Chave derivada sem KDF adequado

```java
byte[] keyBytes = MessageDigest.getInstance("SHA-256").digest(sharedSecret);
```

Um SHA-256 simples sobre o segredo ECDH. O correto é **HKDF** (RFC 5869) com um salt
e um `info` string que ligue a chave ao contexto (`"NetTransfer v1 file transfer"`).

Na prática, com ECDH sobre P-256 o segredo já tem entropia uniforme suficiente e
SHA-256 não é explorável aqui — mas é um desvio da boa prática, e *domain separation*
passa a importar se um dia for adicionado um segundo uso para a mesma chave.

#### Sem validação da chave pública recebida

O código faz `generatePublic(new X509EncodedKeySpec(theirPubBytes))` e usa diretamente.
Não verifica se o ponto está na curva ou se é um ponto de ordem pequena.
**Invalid curve attacks** exploram exatamente isto para extrair a chave privada bit a bit.

Na prática o provider SunEC do JDK faz validação básica ao construir a chave e rejeita
pontos inválidos, portanto há proteção — mas por acidente, não por desenho. Depender de
comportamento não documentado do provider não é aceitável em código de segurança.

#### Records reordenáveis / removíveis

Cada record é cifrado independentemente com IV próprio. GCM garante que **cada record**
é autêntico — mas não que a **sequência** de records esteja intacta. Um atacante que
consiga alterar bytes no fluxo TCP pode teoricamente:

- Remover um record inteiro
- Reordenar records
- Fazer replay de um record anterior

O TCP dificulta isto (sequence numbers, checksums), mas TCP não é uma defesa
criptográfica.

**Solução standard:** usar um **nonce sequencial** em vez de aleatório — contador de
12 bytes incrementado por record — o que faz qualquer reordenação ou omissão falhar
imediatamente na desencriptação. Bónus: elimina o risco (remoto) de colisão de IVs
aleatórios.

#### Metadados do handshake em claro

As chaves públicas são trocadas em plaintext antes da cifra arrancar — normal e
inevitável em ECDH. O `TransferRequest` (nomes de ficheiros, tamanhos, estrutura de
pastas) **já vai cifrado**, o que está correto.

Um observador passivo vê apenas: dois IPs, duas chaves públicas efémeras, e o
volume/timing do tráfego.

#### Superfície de ataque do lado do recetor

**Sem limite de tamanho no `readInt()`**
`EncryptedInputStream.readNextRecord()` lê um `int` e aloca `new byte[recordLen]`.
Um atacante envia `0x7FFFFFFF` → tentativa de alocar 2 GB → `OutOfMemoryError`.
DoS trivial. Falta um sanity check (`if (recordLen > MAX_RECORD) throw`).

**Sem limite de ligações simultâneas**
O `ConnectionManager` cria uma thread por ligação aceite, sem limite. Centenas de
ligações levam a esgotamento de threads.

**Sem rate limiting na descoberta**
Flood de pacotes UDP no porto 54321 faz a lista de peers crescer indefinidamente.

**Diálogo de aceitação como única defesa**
É positivo que exista (o utilizador tem de aceitar explicitamente), mas se alguém
clicar "Accept" por reflexo, os ficheiros são escritos. Não há verificação de tipo de
ficheiro nem aviso para executáveis.

#### Sem verificação de integridade fim-a-fim

Cada record é autenticado, mas não existe hash do ficheiro completo. Um ficheiro
truncado por queda de ligação a meio fica no disco parcialmente escrito, sem qualquer
marca de que está incompleto.

Um `SHA-256` por ficheiro, enviado no `FILE_END` e verificado no recetor, resolveria.

---

## 4. Avaliação global

| Aspeto | Nota | Comentário |
|---|---|---|
| Escolha de primitivas | **Muito bom** | ECDH P-256, AES-256-GCM, SecureRandom — tudo padrão e adequado |
| Forward secrecy | **Muito bom** | Chaves efémeras genuínas, descartadas após uso |
| Confidencialidade (atacante passivo) | **Bom** | Um observador que só escuta não consegue nada |
| Integridade dos dados | **Bom** ao nível do record, **fraco** ao nível do fluxo | GCM protege cada bloco; sequência não é protegida |
| Autenticação | **Ausente** | MITM ativo compromete tudo. Falha mais grave. |
| Robustez a input malicioso | **Fraco** | `readInt()` sem limite = OOM; sem limite de ligações |
| Proteção do sistema de ficheiros | **Bom** | Path traversal bloqueado, duplicados tratados |

### Veredito

A criptografia está bem implementada **para o que faz** — proteger contra alguém que
apenas escuta a rede. Contra um atacante ativo na mesma LAN, a ausência de autenticação
torna toda a cifra decorativa.

- **Rede doméstica de confiança:** razoável.
- **Rede partilhada ou pública:** não é seguro.

---

## 5. Prioridades de correção

### Alto impacto, baixo esforço

1. **Código de verificação de 6 dígitos** derivado das chaves públicas, mostrado em
   ambos os ecrãs — fecha o vetor MITM
2. **Limite máximo no `readInt()`** do `EncryptedInputStream` — fecha o DoS por OOM
3. **Verificação de espaço em disco** antes de aceitar + limpeza de parciais no erro
4. **Broadcast por interface** com endereço de subnet — corrige a descoberta multi-NIC

### Melhorias de correção

5. **HKDF** em vez de SHA-256 direto na derivação da chave
6. **Nonce sequencial** em vez de aleatório — protege a ordem dos records
7. **SHA-256 por ficheiro** no `FILE_END` — integridade fim-a-fim
8. **Limite de ligações simultâneas** no `ConnectionManager`

---

## Notas finais

Esta análise cobre o estado do código no commit `cc23cfa`. As secções 1 e 2 descrevem
lacunas funcionais com impacto direto na experiência de utilização. A secção 3 descreve
o modelo de ameaça atual e as suas limitações.

Nenhuma das correções propostas exige alterar a arquitetura da aplicação — todas são
localizadas em `DiscoveryService`, `Handshake`, `EncryptedInputStream`,
`FileTransferService` e `ConnectionManager`.
