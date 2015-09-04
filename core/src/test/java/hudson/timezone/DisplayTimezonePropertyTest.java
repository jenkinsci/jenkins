package hudson.timezone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;
import org.mockito.Mockito;

import hudson.model.User;
import hudson.timezone.DisplayTimezoneProperty.DescriptorImpl;


public class DisplayTimezonePropertyTest {
	
	@Test
	public void defaultTest() throws Exception {
		DescriptorImpl descriptorImpl = new DisplayTimezoneProperty.DescriptorImpl();
		User user = Mockito.mock(User.class);
		DisplayTimezoneProperty dtp = (DisplayTimezoneProperty) descriptorImpl.newInstance(user);
		
		assertEquals(descriptorImpl.TIMEZONES.get(0), dtp.getDisplayTimezone());
		assertFalse(dtp.getUseTimezone());
	}

}
