<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="/WEB-INF/views/template/head.jsp" %>
<%@ include file="/WEB-INF/views/template/nav.jsp" %>
<div class="row justify-content-center mt-3">
    <div class="col-md-8 col-lg-6">
        <%@ include file="/WEB-INF/views/template/message.jsp" %>
        <h4 class="text-center mb-3"><c:out value="${title}"/></h4>
        <c:choose>
            <c:when test="${not empty token}">
                <div class="card">
                    <div class="card-body">
                        <form action="${pageContext.request.contextPath}/User" method="post">
                            <input type="hidden" name="form" value="reset-password">
                            <input type="hidden" name="_csrf" value="${sessionScope.csrf_token}">
                            <input type="hidden" name="token" value="<c:out value='${token}'/>">
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
                            <button class="btn btn-primary w-100" type="submit">Restablecer contraseña</button>
                        </form>
                    </div>
                </div>
            </c:when>
            <c:otherwise>
                <p class="text-center">
                    <a href="${pageContext.request.contextPath}/User?page=forgot-password">Solicitar un nuevo enlace</a>
                </p>
            </c:otherwise>
        </c:choose>
    </div>
</div>
<%@ include file="/WEB-INF/views/template/scripts.jsp" %>
