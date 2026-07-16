<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<nav class="navbar navbar-expand-lg fixed-top bg-primary" data-bs-theme="dark">
    <div class="container">
        <c:choose>
            <c:when test="${not empty sessionScope.s_id}">
                <a class="navbar-brand" href="${pageContext.request.contextPath}/Poll?page=dashboard">Encuestas</a>
                <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#navbar"
                        aria-controls="navbar" aria-expanded="false" aria-label="Alternar navegación">
                    <span class="navbar-toggler-icon"></span>
                </button>
                <div id="navbar" class="collapse navbar-collapse">
                    <ul class="navbar-nav me-auto">
                        <li class="nav-item">
                            <a class="nav-link" href="${pageContext.request.contextPath}/Poll?page=add">Agregar encuesta</a>
                        </li>
                    </ul>
                    <ul class="navbar-nav">
                        <li class="nav-item dropdown">
                            <a href="#" class="nav-link dropdown-toggle" role="button" data-bs-toggle="dropdown" aria-expanded="false">Usuario</a>
                            <ul class="dropdown-menu dropdown-menu-end">
                                <li><a class="dropdown-item" href="${pageContext.request.contextPath}/User?profile=${sessionScope.s_user}">Ver perfil</a></li>
                                <li><a class="dropdown-item" href="${pageContext.request.contextPath}/User?page=edit-profile">Editar perfil</a></li>
                                <li><a class="dropdown-item" href="${pageContext.request.contextPath}/User?page=change-user">Cambiar usuario</a></li>
                                <li><a class="dropdown-item" href="${pageContext.request.contextPath}/User?page=change-email">Cambiar correo electrónico</a></li>
                                <li><a class="dropdown-item" href="${pageContext.request.contextPath}/User?page=change-password">Cambiar contraseña</a></li>
                                <li><hr class="dropdown-divider"></li>
                                <li><a class="dropdown-item" href="${pageContext.request.contextPath}/User?page=delete-account">Borrar cuenta</a></li>
                                <li>
                                    <form action="${pageContext.request.contextPath}/User" method="post">
                                        <input type="hidden" name="form" value="close-session">
                                        <input type="hidden" name="_csrf" value="${sessionScope.csrf_token}">
                                        <button type="submit" class="dropdown-item">Cerrar sesión</button>
                                    </form>
                                </li>
                            </ul>
                        </li>
                    </ul>
                </div>
            </c:when>
            <c:otherwise>
                <a class="navbar-brand" href="${pageContext.request.contextPath}/">Encuestas</a>
            </c:otherwise>
        </c:choose>
    </div>
</nav>
<main class="container body-content">
