package org.jenkins.ui.icon;

public class BuildStatusIcon extends Icon {
    private boolean inProgress;

    public BuildStatusIcon(String classSpec, String url, String style, boolean inProgress) {
        super(classSpec, url, style, IconType.CORE, IconFormat.EXTERNAL_SVG_SPRITE);
        this.inProgress = inProgress;
    }

    public  BuildStatusIcon(String classSpec, String url, String style) {
        this(classSpec, url, style, false);
    }

    @Override
    public boolean isSvgSprite() {
        return super.isSvgSprite();
    }

    public boolean isBuildStatus() {
        return true;
    }

    public boolean isInProgress() {
       return inProgress;
    }
}
