package hudson.security.csrf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class CrumbFilterTest {

    @Test
    void legacyDefaultCrumbFieldIsUsedWhenPrimaryFieldIsMissing() throws Exception {
        CrumbFilter filter = new CrumbFilter();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Jenkins-Crumb")).thenReturn(null);
        when(request.getHeader(".crumb")).thenReturn("legacy-crumb");
        when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());

        Method method = CrumbFilter.class.getDeclaredMethod("extractCrumbFromRequest", HttpServletRequest.class, String.class);
        method.setAccessible(true);

        assertEquals("legacy-crumb", method.invoke(filter, request, ".crumb"));
    }
}
