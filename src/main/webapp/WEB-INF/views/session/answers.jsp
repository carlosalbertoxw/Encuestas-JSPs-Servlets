<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="/WEB-INF/views/template/head.jsp" %>
<%@ include file="/WEB-INF/views/template/nav.jsp" %>
<div class="mt-3">
    <%@ include file="/WEB-INF/views/template/message.jsp" %>
    <h4 class="text-center mb-1"><c:out value="${title}"/></h4>
    <p class="text-center text-secondary">${result.totalCount} respuesta(s)</p>

    <c:if test="${result.totalCount == 0}">
        <p class="text-center text-secondary">Esta encuesta aún no tiene respuestas.</p>
    </c:if>
    <c:forEach items="${result.items}" var="item">
        <div class="card mb-3">
            <div class="card-body">
                <div class="d-flex justify-content-between align-items-center">
                    <strong><c:out value="${item.userName}"/></strong>
                    <span class="stars" title="${item.stars} de 5 estrellas">
                        <c:forEach begin="1" end="5" var="i">${i le item.stars ? '&#9733;' : '&#9734;'}</c:forEach>
                    </span>
                </div>
                <c:if test="${not empty item.comment}">
                    <p class="card-text mt-2 mb-0"><c:out value="${item.comment}"/></p>
                </c:if>
            </div>
        </div>
    </c:forEach>

    <c:if test="${result.totalPages > 1}">
        <nav aria-label="Paginación de respuestas">
            <ul class="pagination justify-content-center">
                <li class="page-item ${result.hasPrevious ? '' : 'disabled'}">
                    <a class="page-link"
                       href="${pageContext.request.contextPath}/Answer?page=answers&id=${pollId}&p=${result.page - 1}">Anterior</a>
                </li>
                <li class="page-item disabled">
                    <span class="page-link">${result.page} / ${result.totalPages}</span>
                </li>
                <li class="page-item ${result.hasNext ? '' : 'disabled'}">
                    <a class="page-link"
                       href="${pageContext.request.contextPath}/Answer?page=answers&id=${pollId}&p=${result.page + 1}">Siguiente</a>
                </li>
            </ul>
        </nav>
    </c:if>
</div>
<%@ include file="/WEB-INF/views/template/scripts.jsp" %>
