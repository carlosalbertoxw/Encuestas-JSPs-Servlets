package encuestas.data;

import java.util.List;

/**
 * Página de resultados con los totales necesarios para pintar la paginación.
 */
public class PagedResult<T> {

    /** Tamaño de página de los listados. */
    public static final int DEFAULT_PAGE_SIZE = 10;

    private final List<T> items;
    private final int page;
    private final int pageSize;
    private final long totalCount;

    public PagedResult(List<T> items, int page, int pageSize, long totalCount) {
        this.items = items;
        this.page = page;
        this.pageSize = pageSize;
        this.totalCount = totalCount;
    }

    public List<T> getItems() {
        return items;
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public int getTotalPages() {
        return (int) Math.ceil((double) totalCount / pageSize);
    }

    public boolean isHasPrevious() {
        return page > 1;
    }

    public boolean isHasNext() {
        return page < getTotalPages();
    }

    /** Corrige una página fuera de rango recibida en la URL. */
    public static int normalizePage(int page) {
        return Math.max(page, 1);
    }

    public static int normalizePageSize(int pageSize) {
        return pageSize < 1 ? DEFAULT_PAGE_SIZE : pageSize;
    }
}
