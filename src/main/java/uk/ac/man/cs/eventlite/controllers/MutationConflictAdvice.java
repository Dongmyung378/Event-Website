package uk.ac.man.cs.eventlite.controllers;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;
import jakarta.persistence.LockTimeoutException;
import jakarta.servlet.http.HttpServletRequest;
import uk.ac.man.cs.eventlite.exceptions.MutationConflictException;

@ControllerAdvice(assignableTypes = {EventsController.class, VenuesController.class})
public class MutationConflictAdvice {
    @ExceptionHandler({MutationConflictException.class, ConcurrencyFailureException.class,
                       OptimisticLockException.class, PessimisticLockException.class,
                       LockTimeoutException.class, DataIntegrityViolationException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public String conflict(RuntimeException exception, Model model, HttpServletRequest request) {
        String message = exception instanceof MutationConflictException ? exception.getMessage()
            : "Another request changed this record or its related venue. Reload the latest version before trying again.";
        model.addAttribute("conflictMessage", message);
        String path = request.getRequestURI().substring(request.getContextPath().length());
        java.util.regex.Matcher edit = java.util.regex.Pattern.compile("^/(events|venues)/updateSave/([0-9]+)$").matcher(path);
        if (edit.matches()) model.addAttribute("reloadPath", "/" + edit.group(1) + "/update/" + edit.group(2));
        else if (path.equals("/events") || path.equals("/venues")) model.addAttribute("reloadPath", path + "/new");
        return "mutation-conflict";
    }
}
