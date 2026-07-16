package encuestas.data;

/**
 * Resultado de las operaciones de escritura de los repositorios; permite a la capa web
 * distinguir un duplicado (clave única violada) de un error o un registro inexistente.
 */
public enum RepositoryResult {
    SUCCESS,
    DUPLICATE,
    NOT_FOUND,
    ERROR
}
