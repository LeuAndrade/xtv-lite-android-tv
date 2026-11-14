# XTV Lite — Cliente Xbox Cloud Gaming para Android TV (1GB RAM)

Projeto otimizado para aparelhos Android TV de baixo desempenho (1 GB de RAM) com foco em decodificação de vídeo direta via hardware (`MediaCodec`), minimizando uso de memória e latência (`dt`).

## Recursos Implementados
- **Motor Dual**: `Native (WebRTC + MediaCodec)` ou `WebView (xbox.com/play)`
- **3 Decodificadores de Hardware**:
  1. **Compatível**: `DefaultVideoDecoderFactory` padrão do WebRTC.
  2. **Baixa latência própria**: `FastDecoder` com entrega via textura.
  3. **Saída direta MediaCodec**: `SurfaceDecoder` com saída direta na `SurfaceView` (sem textura intermediária).
- **Menu Superior Esquerdo (Botão Voltar)**: Teclado, Controle, Microfone e Menu 3 Pontinhos (Ligar/Desligar Estatísticas, Colar, Sair).
- **Build Automático de APK no GitHub Actions**: Ao subir os arquivos no GitHub, a aba **Actions** gera o `.apk` automaticamente sem precisar instalar Android Studio.
