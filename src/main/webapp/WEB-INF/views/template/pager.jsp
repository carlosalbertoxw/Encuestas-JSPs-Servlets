<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%-- Paginación de requestScope.result (PagedResult). El parámetro baseUrl es la URL del
     listado sin el número de página; se le agrega ?p=N (o &p=N si ya lleva query). --%>
<c:set var="pageSeparator" value="${fn:contains(param.baseUrl, '?') ? '&amp;' : '?'}"/>
<c:if test="${result.totalPages > 1}">
    <nav aria-label="Paginación">
        <ul class="pagination justify-content-center">
            <li class="page-item ${result.hasPrevious ? '' : 'disabled'}">
                <a class="page-link" href="<c:out value='${param.baseUrl}'/>${pageSeparator}p=${result.page - 1}">Anterior</a>
            </li>
            <li class="page-item disabled">
                <span class="page-link">${result.page} / ${result.totalPages}</span>
            </li>
            <li class="page-item ${result.hasNext ? '' : 'disabled'}">
                <a class="page-link" href="<c:out value='${param.baseUrl}'/>${pageSeparator}p=${result.page + 1}">Siguiente</a>
            </li>
        </ul>
    </nav>
</c:if>
