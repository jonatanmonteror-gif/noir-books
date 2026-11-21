# Política de privacidad de Noir Books

Última actualización: 20 de noviembre de 2026

Noir Books es una app para Android que abre libros EPUB y lleva un diario de lectura. La desarrolla Jonatan, como persona física (autónomo) en Cataluña, España. En este texto, «nosotros» se refiere a él.

## Lo esencial

- **No tenemos servidores.** Todo lo que haces en la app se guarda en tu móvil.
- **No hay cuenta, ni publicidad, ni estadísticas de uso, ni rastreadores.** No vendemos ni cedemos datos a nadie.
- La copia en Google Drive es **opcional**. Se guarda en tu propio Drive y nosotros no tenemos acceso a ella.

## Qué datos guarda la app y dónde

La app guarda en la memoria privada del móvil, a la que otras apps no pueden acceder:

- Los libros que importas y sus portadas.
- Tu progreso, marcadores, subrayados, notas y citas.
- Tus sesiones de lectura, metas, estanterías, valoraciones y opiniones.
- Las palabras que guardas del diccionario.
- Tus ajustes y un registro de errores. Ese registro solo sale del móvil si tú lo copias o lo compartes.

Si desinstalas la app o borras sus datos desde los ajustes de Android, todo esto se elimina.

## Conexiones a internet

La app solo se conecta a internet cuando usas una función que lo necesita. En cada caso envía únicamente lo imprescindible:

| Función | Servicio | Qué se envía |
|---|---|---|
| Buscar un libro o escanear su ISBN para añadirlo | Google Books (Google) y Open Library (Internet Archive) | El título, el autor o el ISBN que buscas |
| Portadas y sinopsis de libros añadidos a mano | Google Books y Open Library | El título, el autor o el ISBN del libro |
| Definir una palabra | Diccionario de la Real Academia Española (dle.rae.es) | La palabra que consultas |
| Copia en Google Drive (si la activas) | Google Drive | La copia de seguridad de tus datos (ver más abajo) |
| Buscar actualizaciones (solo la versión descargada de GitHub, no la de Google Play) | GitHub | Una consulta de la última versión publicada. No se envía ningún dato tuyo |

Estos servicios reciben, como cualquier web, la dirección IP de tu conexión. Se rigen por sus propias políticas de privacidad: [Google](https://policies.google.com/privacy), [Internet Archive](https://archive.org/about/terms.php), [RAE](https://www.rae.es/aviso-legal) y [GitHub](https://docs.github.com/es/site-policy/privacy-policies/github-general-privacy-statement).

**El texto de tus libros nunca se envía a ningún sitio.**

## Funciones que trabajan dentro del móvil

- **Escáner de códigos de barras y cita desde foto.** Usan las herramientas de Google (Servicios de Google Play y ML Kit), que reconocen la imagen en el propio móvil. Las fotos no se guardan ni se envían.
- **Lectura en voz alta.** Usa el motor de voz que tengas instalado en Android. Según cómo lo tengas configurado, ese motor puede funcionar con conexión. Se rige por la política de su proveedor, normalmente Google.

## Copia en Google Drive

Si vinculas Google Drive, la app pide un único permiso, `drive.appdata`. Ese permiso solo permite usar una carpeta privada y oculta de tu Drive, reservada a Noir Books:

- La app **no puede ver, leer ni modificar ningún otro archivo de tu Drive.**
- La copia va directamente de tu móvil a tu Drive, sin pasar por ningún servidor nuestro.
- La copia incluye tus datos de lectura y ajustes. Incluye también los archivos de los libros si activas esa opción.
- Puedes borrarla y quitar el acceso cuando quieras. En Google Drive, ve a Configuración → Administrar aplicaciones → Noir Books; o en tu cuenta de Google, a Seguridad → Aplicaciones de terceros.

El uso de la información recibida de las API de Google se ajusta a la [Política de datos de usuario de los servicios de API de Google](https://developers.google.com/terms/api-services-user-data-policy), incluidos sus requisitos de uso limitado.

## Permisos de Android

- **Internet:** para las funciones de la tabla anterior.
- **Notificaciones:** para el cronómetro de lectura y los controles de la lectura en voz alta.
- **Servicio en primer plano (reproducción):** para que la lectura en voz alta siga con la pantalla apagada.
- **Instalar aplicaciones (solo la versión de GitHub):** para instalar las actualizaciones que descargas desde la app. Antes de instalarlas, la app comprueba su huella SHA-256.

La app no pide acceso a tu ubicación, contactos, micrófono ni a tus archivos en general. Los libros los eliges tú con el selector de Android.

## Menores

La app no está dirigida a menores de 14 años y no recoge datos de nadie.

## Tus derechos

Como no recogemos datos en ningún servidor, no tenemos datos tuyos que consultar, corregir o borrar: los controlas tú desde el móvil y desde tu Drive. Si tienes alguna duda, escríbenos a noirbooks@gmail.com. También tienes derecho a reclamar ante la Agencia Española de Protección de Datos ([aepd.es](https://www.aepd.es)).

## Cambios

Si esta política cambia, actualizaremos la fecha de arriba y lo indicaremos en las novedades de la app.

## Contacto

Jonatan (Noir Books)

- Correo: [noirbooks@gmail.com](mailto:noirbooks@gmail.com)
- También: https://github.com/jonatanmonteror-gif/noir-books/issues
