# Datos sintéticos

Estos CSV preparan dos usuarios y tres cuentas ficticias para las pruebas de aceptación. Los `subject` se usarán como identificadores de usuario al configurar Keycloak en H2.

El perfil `local` de Contabilidad carga estos mismos identificadores y saldos mediante `db/local/V2__seed_cuentas_demo.sql`. El perfil por defecto crea solo el esquema; las pruebas insertan sus propios datos y no dependen del seed local.

| Usuario | Cuenta | Saldo inicial |
| --- | --- | ---: |
| cliente1 | `aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1` | 4000.00 PEN |
| cliente1 | `bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1` | 100.00 PEN |
| cliente2 | `cccccccc-cccc-4ccc-8ccc-ccccccccccc1` | 200.00 PEN |
