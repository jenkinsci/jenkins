/*
 The MIT License

 Copyright (c) 2011, Dominik Bartholdi

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE.
 */
package hudson.maven.settings;

import hudson.Extension;

import jenkins.configprovider.AbstractConfigProvider;
import hudson.maven.settings.Messages;
import jenkins.configprovider.model.Config;
import jenkins.configprovider.model.ConfigDescription;
import jenkins.configprovider.model.ContentType;
import jenkins.configprovider.model.ContentType.DefinedType;

@Extension
public class MavenSettingsProvider extends AbstractConfigProvider {

	@Override
	public ConfigDescription getConfigDescription() {
		return new ConfigDescription(Messages.mvn_settings_provider_name(),
				Messages.mvn_settings_provider_description());
	}

	@Override
	public Config newConfig() {
		String id = this.getProviderId() + System.currentTimeMillis();
		return new Config(id, "MySettings", "", "<settings></settings>");
	}

	@Override
	protected String getXmlFileName() {
		return "maven-settings-files.xml";
	}

	@Override
	public ContentType getContentType() {
		return DefinedType.HTML;
	}
}
