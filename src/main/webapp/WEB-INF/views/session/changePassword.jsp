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
                <form action="${pageContext.request.contextPath}/User" method="post">
                    <input type="hidden" name="form" value="change-password">
                    <input type="hidden" name="_csrf" value="${sessionScope.csrf_token}">
                    <div class="mb-3">
                        <label class="form-label" for="newPassword">Nueva contraseña</label>
                        <input class="form-control" type="password" name="new_password" id="newPassword"
                               autocomplete="new-password" minlength="6" maxlength="50" required>
                    </div>
                    <div class="mb-3">
                        <label class="form-label" for="reNewPassword">Repita nueva contraseña</label>
                        <input class="form-control" type="password" name="re_new_password" id="reNewPassword"
                               autocomplete="new-password" minlength="6" maxlength="50" required
                               data-match="newPassword">
                    </div>
                    <div class="mb-3">
                        <label class="form-label" for="password">Contraseña actual</label>
                        <input class="form-control" type="password" name="password" id="password"
                               autocomplete="current-password" maxlength="50" required>
                    </div>
                    <button class="btn btn-primary w-100" type="submit">Guardar</button>
                </form>
            </div>
        </div>
    </div>
</div>
<%@ include file="/WEB-INF/views/template/scripts.jsp" %>
