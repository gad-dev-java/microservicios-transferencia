# Datos sintéticos

Estos CSV describen dos usuarios y cuatro cuentas ficticias para las pruebas de aceptación. Los `subject` coinciden con los identificadores de los usuarios de Keycloak.

El perfil `local` de Contabilidad carga estos identificadores y saldos mediante `db/local/V2__seed_cuentas_demo.sql` y `V3__seed_inactive_destination.sql`. El perfil por defecto crea solo el esquema; las pruebas insertan sus propios datos.

| Usuario | Cuenta | Saldo inicial |
| --- | --- | ---: |
| cliente1 | `aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1` | 4000.00 PEN |
| cliente1 | `bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1` | 100.00 PEN |
| cliente2 | `cccccccc-cccc-4ccc-8ccc-ccccccccccc1` | 200.00 PEN |
| cliente2 | `dddddddd-dddd-4ddd-8ddd-ddddddddddd1` (inactiva) | 0.00 PEN |
