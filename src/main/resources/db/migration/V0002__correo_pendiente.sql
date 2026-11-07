-- * Amplía los correos a 254 caracteres (el máximo que admite una dirección válida).
-- * Correo pendiente: un cambio de correo solo se aplica cuando se confirma desde la
--   dirección nueva; mientras tanto la cuenta sigue usando la anterior.

ALTER TABLE a_users
  MODIFY u_email VARCHAR(254) NOT NULL,
  ADD COLUMN u_pending_email VARCHAR(254) NULL AFTER u_email_confirmed;
