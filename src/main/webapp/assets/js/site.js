// Comportamiento del lado cliente sin scripts en línea (compatible con la CSP).
// La validación aquí es solo una mejora de experiencia de usuario: la política real
// se aplica en el servidor (encuestas.service.Validation).
(function () {
    "use strict";

    function messageFor(field) {
        var v = field.validity;
        if (v.valueMissing) {
            return "Este campo es obligatorio";
        }
        if (v.typeMismatch && field.type === "email") {
            return "Ingresa un correo electrónico válido";
        }
        if (v.tooShort) {
            return "Debe tener al menos " + field.minLength + " caracteres";
        }
        if (v.tooLong) {
            return "Debe tener como máximo " + field.maxLength + " caracteres";
        }
        if (v.patternMismatch) {
            return field.title || "El formato no es válido";
        }
        if (v.rangeUnderflow || v.rangeOverflow || v.badInput) {
            return "El valor debe estar entre " + field.min + " y " + field.max;
        }
        // Confirmación de contraseña: el campo declara data-match="idDelOriginal".
        var matchId = field.getAttribute("data-match");
        if (matchId) {
            var other = document.getElementById(matchId);
            if (other && field.value !== other.value) {
                return "Las contraseñas no coinciden";
            }
        }
        return "";
    }

    function createMessageSpan() {
        var span = document.createElement("span");
        span.className = "field-validation text-danger small d-block";
        return span;
    }

    function messageContainer(field) {
        // Para los grupos de radios el mensaje va al final del bloque, no junto a cada opción.
        if (field.type === "radio") {
            var group = field.closest(".mb-3") || field.parentElement;
            var groupSpan = group.querySelector(".field-validation");
            if (!groupSpan) {
                groupSpan = createMessageSpan();
                group.appendChild(groupSpan);
            }
            return groupSpan;
        }
        var next = field.nextElementSibling;
        if (next && next.classList.contains("field-validation")) {
            return next;
        }
        var span = createMessageSpan();
        field.insertAdjacentElement("afterend", span);
        return span;
    }

    function validateField(field) {
        var message = messageFor(field);
        var span = messageContainer(field);
        span.textContent = message;
        if (field.type !== "radio") {
            field.classList.toggle("is-invalid", message !== "");
        }
        return message === "";
    }

    function validateForm(form) {
        var valid = true;
        var fields = form.querySelectorAll("input, textarea, select");
        var radioGroups = {};
        fields.forEach(function (field) {
            if (field.type === "hidden" || field.type === "submit") {
                return;
            }
            if (field.type === "radio") {
                if (radioGroups[field.name]) {
                    return;
                }
                radioGroups[field.name] = true;
            }
            if (!validateField(field)) {
                valid = false;
            }
        });
        return valid;
    }

    document.querySelectorAll("form").forEach(function (form) {
        // Se sustituyen las burbujas nativas del navegador por mensajes bajo cada campo.
        form.setAttribute("novalidate", "novalidate");
        form.addEventListener("submit", function (event) {
            if (!validateForm(form)) {
                event.preventDefault();
                event.stopImmediatePropagation();
                return;
            }
            // Confirmación para formularios marcados con data-confirm (p. ej. borrar).
            var confirmText = form.getAttribute("data-confirm");
            if (confirmText && !window.confirm(confirmText)) {
                event.preventDefault();
            }
        });
        form.querySelectorAll("input, textarea, select").forEach(function (field) {
            field.addEventListener("blur", function () {
                field.dataset.touched = "true";
                validateField(field);
            });
            field.addEventListener("input", function () {
                if (field.dataset.touched) {
                    validateField(field);
                }
            });
        });
    });
})();
