# Datos sintéticos

Estos CSV preparan dos usuarios y tres cuentas ficticias para las pruebas de aceptación. Los `subject` se usarán como identificadores de usuario al configurar Keycloak en H2.

La carga automática en PostgreSQL llegará con las migraciones y el cargador de datos de Contabilidad en H1. En H0, los CSV son contratos de prueba y no crean tablas ni saldos.

| Usuario | Cuenta | Saldo inicial |
| --- | --- | ---: |
| cliente1 | `aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1` | 4000.00 PEN |
| cliente1 | `bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1` | 100.00 PEN |
| cliente2 | `cccccccc-cccc-4ccc-8ccc-ccccccccccc1` | 200.00 PEN |
