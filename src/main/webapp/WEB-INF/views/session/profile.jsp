<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="/WEB-INF/views/template/head.jsp" %>
<%@ include file="/WEB-INF/views/template/nav.jsp" %>
<div class="mt-3">
    <%@ include file="/WEB-INF/views/template/message.jsp" %>
    <h4 class="text-center mb-3"><c:out value="${title}"/></h4>
    <c:if test="${empty polls}">
        <p class="text-center text-secondary">Este usuario aún no tiene encuestas.</p>
    </c:if>
    <c:forEach items="${polls}" var="item">
        <div class="card mb-3">
            <div class="card-body">
                <h5 class="card-title"><c:out value="${item.title}"/></h5>
                <p class="card-text"><c:out value="${item.description}"/></p>
            </div>
            <div class="card-footer">
                <a class="btn btn-sm btn-outline-primary"
                   href="${pageContext.request.contextPath}/Answer?page=add&id=${item.id}">Responder</a>
            </div>
        </div>
    </c:forEach>
</div>
<%@ include file="/WEB-INF/views/template/scripts.jsp" %>
