<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="/WEB-INF/views/template/head.jsp" %>
<%@ include file="/WEB-INF/views/template/nav.jsp" %>
<div class="row mt-3 g-4">
    <div class="col-12">
        <%@ include file="/WEB-INF/views/template/message.jsp" %>
    </div>
    <div class="col-md-6">
        <div class="card">
            <div class="card-body">
                <h4 class="card-title text-center mb-3">Acceso</h4>
                <form action="${pageContext.request.contextPath}/User" method="post">
                    <input type="hidden" name="form" value="sign-in">
                    <input type="hidden" name="_csrf" value="${sessionScope.csrf_token}">
                    <c:set var="loginEmail" value="${old.form eq 'sign-in' ? old.email : ''}"/>
                    <div class="mb-3">
                        <label class="form-label" for="signInEmail">Correo electrónico</label>
                        <input class="form-control" type="email" name="email" id="signInEmail"
                               autocomplete="username" maxlength="50" required
                               value="<c:out value='${loginEmail}'/>">
                    </div>
                    <div class="mb-3">
                        <label class="form-label" for="signInPassword">Contraseña</label>
                        <input class="form-control" type="password" name="password" id="signInPassword"
                               autocomplete="current-password" maxlength="50" required>
                    </div>
                    <button class="btn btn-primary w-100" type="submit">Entrar</button>
                    <div class="text-center mt-2">
                        <a href="${pageContext.request.contextPath}/User?page=forgot-password">¿Olvidaste tu contraseña?</a>
                    </div>
                </form>
            </div>
        </div>
    </div>
    <div class="col-md-6">
        <div class="card">
            <div class="card-body">
                <h4 class="card-title text-center mb-3">Registro</h4>
                <form action="${pageContext.request.contextPath}/User" method="post">
                    <input type="hidden" name="form" value="sign-up">
                    <input type="hidden" name="_csrf" value="${sessionScope.csrf_token}">
                    <c:set var="registerEmail" value="${old.form eq 'sign-up' ? old.email : ''}"/>
                    <div class="mb-3">
                        <label class="form-label" for="signUpEmail">Correo electrónico</label>
                        <input class="form-control" type="email" name="email" id="signUpEmail"
                               autocomplete="email" maxlength="50" required
                               value="<c:out value='${registerEmail}'/>">
                    </div>
                    <div class="mb-3">
                        <label class="form-label" for="signUpPassword">Contraseña</label>
                        <input class="form-control" type="password" name="password" id="signUpPassword"
                               autocomplete="new-password" minlength="6" maxlength="50" required>
                    </div>
                    <div class="mb-3">
                        <label class="form-label" for="signUpRePassword">Repita contraseña</label>
                        <input class="form-control" type="password" name="re_password" id="signUpRePassword"
                               autocomplete="new-password" minlength="6" maxlength="50" required
                               data-match="signUpPassword">
                    </div>
                    <button class="btn btn-primary w-100" type="submit">Registrarse</button>
                </form>
            </div>
        </div>
    </div>
</div>
<%@ include file="/WEB-INF/views/template/scripts.jsp" %>
