# Driver Profit

App Android para saber cuánto te queda **neto** manejando con DiDi: mide los km con el GPS del celular
(sirve aunque el odómetro del auto no funcione), calcula en vivo lo gastado en nafta según el consumo
del auto y el precio del litro, y lo cruza con lo que te muestra DiDi al cerrar el turno.

Pensada para un Fiat Palio 2017 1.4 Fire (9,5 L/100 km en ciudad), pero el auto, el consumo y el
precio se cambian en Ajustes.

## Qué hace

- **Turno con inicio / pausa / fin.** Corre como servicio en segundo plano con una notificación fija
  que muestra km, nafta gastada y neto, con botones Pausar y Terminar. Sigue midiendo mientras DiDi
  está en pantalla o con la pantalla apagada.
- **Costo en vivo.** km recorridos × consumo × precio del litro. Si anotás cada viaje con "+ Viaje",
  también ves bruto, neto y porcentaje en vivo.
- **Cierre del turno con los datos de DiDi.** Tarifa del viaje, premio, propina, otros, cantidad de
  viajes y "ganancias por km" (de ahí se deducen los km con pasajero y los km en vacío).
- **Cargas de nafta.** Importe + precio del litro → litros. Saldo estimado del tanque, km que rinde,
  cuánto recuperaste de cada carga y consumo observado a lo largo del tiempo.
- **Historial** por día, semana y mes, con neto por km y por hora. Exportación a CSV.
- Todo se guarda en el teléfono. Sin cuenta ni servidor.

## Instalar

1. Entrá a la pestaña **Actions** del repo, abrí la última corrida verde y bajá el artefacto
   `driver-profit-debug-apk` (o bajalo desde **Releases** si hay una versión etiquetada).
2. Pasá el APK al celular y abrilo. Android te va a pedir permitir "instalar apps de origen desconocido".
3. Al iniciar el primer turno, aceptá los permisos de **ubicación** y **notificaciones**.
4. En Ajustes del teléfono, poné Driver Profit como "Sin restricciones" en optimización de batería
   para que no corte el GPS en turnos largos (hay un botón en la pestaña Ajustes de la app).

## Desarrollo

- `core/`: lógica pura en Kotlin (cálculos, filtro de GPS, reportes, CSV). Se testea con
  `./gradlew :core:test` sin necesidad del SDK de Android.
- `app/`: app Android (Jetpack Compose, servicio de ubicación en primer plano). Se compila con
  `./gradlew :app:assembleDebug` si hay SDK de Android (`ANDROID_HOME`).
- El workflow de GitHub Actions corre los tests y publica el APK en cada push.

## Cómo calcula

| Dato | Fórmula |
|---|---|
| Litros cargados | importe ÷ precio del litro |
| Costo por km | consumo (L/100 km) ÷ 100 × precio del litro |
| Nafta del turno | km medidos × costo por km |
| Neto | bruto de DiDi − nafta del turno |
| Km con pasajero | bruto ÷ "ganancias por km" de DiDi (o lo cargás a mano) |
| Km en vacío | km medidos − km con pasajero |
| Consumo observado | litros cargados ÷ km medidos entre la primera y la última carga |

El GPS descarta lecturas con precisión peor que 30 m, movimientos menores a unos metros cuando el auto
está parado y saltos imposibles, así el "temblor" del GPS no suma km de más.
