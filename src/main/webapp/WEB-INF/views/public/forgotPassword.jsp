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
                <p class="text-secondary">Ingresa tu correo y te enviaremos un enlace para restablecer la contraseña.</p>
                <form action="${pageContext.request.contextPath}/User" method="post">
                    <input type="hidden" name="form" value="forgot-password">
                    <input type="hidden" name="_csrf" value="${sessionScope.csrf_token}">
                    <div class="mb-3">
                        <label class="form-label" for="email">Correo electrónico</label>
                        <input class="form-control" type="email" name="email" id="email"
                               autocomplete="email" maxlength="50" required
                               value="<c:out value='${old.email}'/>">
                    </div>
                    <button class="btn btn-primary w-100" type="submit">Enviar enlace</button>
                </form>
            </div>
        </div>
    </div>
</div>
<%@ include file="/WEB-INF/views/template/scripts.jsp" %>
