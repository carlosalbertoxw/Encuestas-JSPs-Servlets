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
                <p class="text-danger">Esta acción es permanente: se eliminarán tu perfil, tus encuestas y sus respuestas.</p>
                <form action="${pageContext.request.contextPath}/User" method="post"
                      data-confirm="¿Estás seguro de borrar tu cuenta? Esta acción no se puede deshacer.">
                    <input type="hidden" name="form" value="delete-account">
                    <input type="hidden" name="_csrf" value="${sessionScope.csrf_token}">
                    <div class="mb-3">
                        <label class="form-label" for="password">Contraseña</label>
                        <input class="form-control" type="password" name="password" id="password"
                               autocomplete="current-password" maxlength="50" required>
                    </div>
                    <button class="btn btn-danger w-100" type="submit">Borrar cuenta</button>
                </form>
            </div>
        </div>
    </div>
</div>
<%@ include file="/WEB-INF/views/template/scripts.jsp" %>
