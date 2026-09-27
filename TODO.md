# TODO

- [ ] VMs de teste macOS/Windows: Mojave 10.14 não serve — JavaFX 21 requer macOS 11+ (pipeline gráfico Metal). Tiny10 é build "debloated" não oficial, pode dar falsos negativos por faltarem componentes do Windows. Trocar por macOS 11+ real (Big Sur/Ventura/Sonoma) e Windows 10/11 de avaliação oficial (Microsoft, 90 dias, pronto para VMware).
- [ ] Guard de instância única: correr o app duas vezes no mesmo host faz a segunda instância falhar o bind da porta UDP 54321 silenciosamente (thread do recetor morre, sem aviso na UI). Detetar isto no arranque (tentar bind antes de abrir janela) e mostrar erro claro ou impedir segunda instância, em vez de falhar caladamente.
- [ ] jpackage + CI matrix (Windows/macOS/Linux) para instaladores nativos — abordagem já combinada com o utilizador, a aguardar confirmação para avançar.
