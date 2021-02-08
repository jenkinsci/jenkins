package org.jenkins.ui.icon;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
public class WeatherIcon extends Icon {
    enum  Status {
        POURING("build-status/weather-sprite.svg#weather-pouring"),
        RAINY("build-status/weather-sprite.svg#weather-rainy"),
        CLOUDY("build-status/weather-sprite.svg#weather-cloudy"),
        PARTLY_CLOUDY("build-status/weather-sprite.svg#weather-partly-cloudy"),
        SUNNY("build-status/weather-sprite.svg#weather-sunny");

        private String url;

        Status(String url) {
           this.url = url;
        }
    }

    public WeatherIcon(String classSpec, String style, Status status) {
        super(classSpec, status.url, style, IconType.CORE, IconFormat.EXTERNAL_SVG_SPRITE);
    }

    @Override
    public boolean isSvgSprite() {
        return true;
    }
}
