-- Limpa registros legados de login que existiam na auditoria
DELETE FROM auditoria WHERE tipo_acao IN ('LOGIN_SUCESSO', 'LOGIN_FALHA');
