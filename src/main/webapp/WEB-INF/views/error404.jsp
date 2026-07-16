<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" isErrorPage="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="title" value="Error 404" scope="request"/>
<%@ include file="/WEB-INF/views/template/head.jsp" %>
<%@ include file="/WEB-INF/views/template/nav.jsp" %>
<div class="row justify-content-center mt-5">
    <div class="col-md-8 text-center">
        <h1 class="display-4">404</h1>
        <p class="lead">La página que buscas no existe o fue movida.</p>
        <a class="btn btn-primary" href="${pageContext.request.contextPath}/">Volver al inicio</a>
    </div>
</div>
<%@ include file="/WEB-INF/views/template/scripts.jsp" %>
