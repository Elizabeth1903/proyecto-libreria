# Lectora AO3 💗

Lector de Archive of Our Own para Android que traduce las obras al español con IA, siguiendo el contexto:
fandom, personajes, relaciones, resumen, los párrafos anteriores y un glosario que crece con cada capítulo.

## Qué hace
- Navegas AO3 normal (con tu cuenta, kudos, comentarios, marcadores).
- Al abrir un capítulo se traduce solo, párrafo a párrafo, conservando cursivas, negritas y saltos de línea.
- Título, resumen, notas del autor, títulos de capítulo y etiquetas también se traducen.
- Menús y botones de AO3 en español (diccionario local, gratis).
- Glosario por obra (apodos, términos mágicos, lugares…) editable a mano.
- Traducciones guardadas en el celular: releer no gasta nada.
- Botón para alternar original/traducción; doble toque en un párrafo = ver su original.
- Español latino, de España o rioplatense; raya de diálogo; honoríficos; instrucciones propias.
- Modo oscuro de lectura y tamaño de letra.
- Abre enlaces de AO3 desde otras apps o con "Compartir".

## Proveedores de IA (pones tu propia API key)
- **Google Gemini**: tiene nivel gratuito → https://aistudio.google.com/apikey
- **Anthropic Claude**: https://console.anthropic.com
- **Compatible con OpenAI**: OpenAI, OpenRouter, DeepSeek, modelos locales…

## Conseguir el APK con GitHub (se puede desde el celular)
1. Crea un repositorio nuevo en github.com.
2. Sube el archivo `LectoraAO3.zip` tal cual (Add file → Upload files).
3. Add file → Create new file, nombre: `.github/workflows/build.yml`, y pega el contenido de `.github/workflows/build.yml`.
4. Espera ~5 min en la pestaña **Actions**. El APK aparece en **Releases** (`LectoraAO3.apk`).
5. Descárgalo en el celu e instálalo (permite "instalar apps desconocidas").

Desde PC también puedes descomprimir el zip, subir la carpeta completa o abrirla en Android Studio.
