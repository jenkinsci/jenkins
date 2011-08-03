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
package jenkins.configprovider;

import hudson.BulkChange;
import hudson.XmlFile;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import jenkins.configprovider.model.Config;
import jenkins.configprovider.model.ConfigDescription;
import jenkins.model.Jenkins;

public abstract class AbstractConfigProvider extends ConfigProvider implements Saveable {

	protected final String ID_PREFIX = this.getClass().getSimpleName() + ".";

	protected Map<String, Config> configs = new HashMap<String, Config>();

	public AbstractConfigProvider() {
		load();
	}

	@Override
	public Collection<Config> getAllConfigs() {
		return Collections.unmodifiableCollection(configs.values());
	}

	@Override
	public Config getConfigById(String configId) {
		return configs.get(configId);
	}

	@Override
	public abstract ConfigDescription getConfigDescription();

	@Override
	public String getProviderId() {
		return ID_PREFIX;
	}

	@Override
	public boolean isResponsibleFor(String configId) {
		return configId != null && configId.startsWith(ID_PREFIX);
	}

	@Override
	public Config newConfig() {
		String id = this.getProviderId() + System.currentTimeMillis();
		return new Config(id, null, null, null);
	}

	@Override
	public void remove(String configId) {
		configs.remove(configId);
		this.save();
	}

	@Override
	public void save(Config config) {
		configs.put(config.id, config);
		this.save();
	}

	/**
	 * @see hudson.model.Saveable#save()
	 */
	public void save() {
		if (BulkChange.contains(this))
			return;
		try {
			getConfigXml().write(this);
			SaveableListener.fireOnChange(this, getConfigXml());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	protected void load() {
		XmlFile xml = getConfigXml();
		if (xml.exists()) {
			try {
				xml.unmarshal(this);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	protected XmlFile getConfigXml() {
		return new XmlFile(Jenkins.XSTREAM, new File(Jenkins.getInstance().getRootDir(), this.getXmlFileName()));
	}

	protected abstract String getXmlFileName();

}
