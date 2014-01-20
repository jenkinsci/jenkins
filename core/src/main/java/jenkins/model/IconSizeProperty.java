package jenkins.model;

import hudson.Extension;
import hudson.model.Messages;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Stores the prefered icon size
 *
 * @author Bruno Meneguello<bruno@meneguello.com>
 */
public class IconSizeProperty extends UserProperty {

	private static final Pattern ICON_SIZE = Pattern.compile("(\\d+)x(\\d+)");

	private Integer width = 32;

	private Integer height = 32;

	public IconSizeProperty() {

	}

	@DataBoundConstructor
	public IconSizeProperty(String dimension) {
		_setDimension(dimension);
	}

	public String getDimension() {
		return String.format("%dx%d", width, height);
	}

	public Integer getWidth() {
		return width;
	}

	public Integer getHeight() {
		return height;
	}

	public void setDimension(String dimension) throws IOException {
		_setDimension(dimension);
		user.save();
	}

	private void _setDimension(String dimension) {
		final Matcher matcher = ICON_SIZE.matcher(dimension);
		if (!matcher.matches()) throw new IllegalArgumentException("Invalid dimension format");

		width = Integer.parseInt(matcher.group(1));
		height = Integer.parseInt(matcher.group(2));
	}

	public void setDimension(Integer width, Integer height) throws IOException {
		_setDimension(width, height);
		user.save();
	}

	private void _setDimension(Integer width, Integer height) {
		this.width = width;
		this.height = height;
	}

	@Extension
	public static class DescriptorImpl extends UserPropertyDescriptor {

		@Override
		public UserProperty newInstance(User user) {
			return new IconSizeProperty();
		}

		@Override
		public String getDisplayName() {
			return Messages.IconSizeProperty_GlobalAction_DisplayName();
		}

	}

}
