#version 450
// a imagem já montada (jogo, barras pretas, FPS, aviso) copiada para a imagem da tela
layout(set = 0, binding = 0) uniform sampler2D img;
layout(location = 0) in vec2 uv;
layout(location = 0) out vec4 cor;
void main() { cor = vec4(texture(img, uv).rgb, 1.0); }
