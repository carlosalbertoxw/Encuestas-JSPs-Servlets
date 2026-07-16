<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="/WEB-INF/views/template/head.jsp" %>
<%@ include file="/WEB-INF/views/template/nav.jsp" %>
<div class="mt-3">
    <%@ include file="/WEB-INF/views/template/message.jsp" %>
    <h4 class="text-center mb-3"><c:out value="${title}"/></h4>
    <c:if test="${empty polls}">
        <p class="text-center text-secondary">Aún no tienes encuestas. Usa «Agregar encuesta» para crear la primera.</p>
    </c:if>
    <c:forEach items="${polls}" var="item">
        <div class="card mb-3">
            <div class="card-body">
                <h5 class="card-title"><c:out value="${item.title}"/></h5>
                <p class="card-text"><c:out value="${item.description}"/></p>
            </div>
            <div class="card-footer d-flex flex-wrap gap-2">
                <a class="btn btn-sm btn-outline-primary"
                   href="${pageContext.request.contextPath}/Poll?page=edit&id=${item.id}">Editar</a>
                <a class="btn btn-sm btn-outline-secondary"
                   href="${pageContext.request.contextPath}/Answer?page=answers&id=${item.id}">Ver respuestas</a>
                <a class="btn btn-sm btn-outline-secondary"
                   href="${pageContext.request.contextPath}/Answer?page=add&id=${item.id}">Responder</a>
                <form action="${pageContext.request.contextPath}/Poll" method="post" class="ms-auto"
                      data-confirm="¿Estás seguro de borrar este registro?">
                    <input type="hidden" name="form" value="delete">
                    <input type="hidden" name="id" value="${item.id}">
                    <input type="hidden" name="_csrf" value="${sessionScope.csrf_token}">
                    <button type="submit" class="btn btn-sm btn-outline-danger">Borrar</button>
                </form>
            </div>
        </div>
    </c:forEach>
</div>
<%@ include file="/WEB-INF/views/template/scripts.jsp" %>
