<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="/WEB-INF/views/template/head.jsp" %>
<%@ include file="/WEB-INF/views/template/nav.jsp" %>
<div class="row justify-content-center mt-3">
    <div class="col-md-8 col-lg-6">
        <%@ include file="/WEB-INF/views/template/message.jsp" %>
        <h4 class="text-center mb-3"><c:out value="${title}"/></h4>
        <div class="card mb-3">
            <div class="card-body">
                <h5 class="card-title"><c:out value="${poll.title}"/></h5>
                <p class="card-text"><c:out value="${poll.description}"/></p>
            </div>
        </div>
        <div class="card">
            <div class="card-body">
                <form action="${pageContext.request.contextPath}/Answer" method="post">
                    <input type="hidden" name="form" value="add">
                    <input type="hidden" name="id" value="${poll.id}">
                    <input type="hidden" name="_csrf" value="${sessionScope.csrf_token}">
                    <div class="mb-3">
                        <span class="form-label d-block">Estrellas</span>
                        <c:forEach begin="1" end="5" var="i">
                            <div class="form-check form-check-inline">
                                <input class="form-check-input" type="radio" name="stars" id="stars${i}"
                                       value="${i}" required>
                                <label class="form-check-label" for="stars${i}">${i}</label>
                            </div>
                        </c:forEach>
                    </div>
                    <div class="mb-3">
                        <label class="form-label" for="comment">Comentario</label>
                        <textarea class="form-control" rows="3" name="comment" id="comment" maxlength="1000"></textarea>
                    </div>
                    <button class="btn btn-primary w-100" type="submit">Enviar respuesta</button>
                </form>
            </div>
        </div>
    </div>
</div>
<%@ include file="/WEB-INF/views/template/scripts.jsp" %>
