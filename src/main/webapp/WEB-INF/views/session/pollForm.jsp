<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="/WEB-INF/views/template/head.jsp" %>
<%@ include file="/WEB-INF/views/template/nav.jsp" %>
<div class="row justify-content-center mt-3">
    <div class="col-md-8 col-lg-6">
        <%@ include file="/WEB-INF/views/template/message.jsp" %>
        <h4 class="text-center mb-3"><c:out value="${title}"/></h4>
        <div class="card">
            <div class="card-body">
                <form action="${pageContext.request.contextPath}/Poll" method="post">
                    <input type="hidden" name="form" value="${poll.id == 0 ? 'add' : 'update'}">
                    <input type="hidden" name="id" value="${poll.id}">
                    <input type="hidden" name="_csrf" value="${sessionScope.csrf_token}">
                    <div class="mb-3">
                        <label class="form-label" for="pollTitle">Título</label>
                        <input class="form-control" name="title" id="pollTitle" maxlength="250" required
                               value="<c:out value='${poll.title}'/>">
                    </div>
                    <div class="mb-3">
                        <label class="form-label" for="pollDescription">Descripción</label>
                        <textarea class="form-control" rows="3" name="description" id="pollDescription"
                                  maxlength="500" required><c:out value="${poll.description}"/></textarea>
                    </div>
                    <div class="mb-3">
                        <label class="form-label" for="pollPosition">Posición</label>
                        <input class="form-control" type="number" min="1" max="999999" name="position"
                               id="pollPosition" required value="${poll.id == 0 ? '' : poll.position}">
                    </div>
                    <button class="btn btn-primary w-100" type="submit">Guardar</button>
                </form>
            </div>
        </div>
    </div>
</div>
<%@ include file="/WEB-INF/views/template/scripts.jsp" %>
