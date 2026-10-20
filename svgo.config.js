// Settings used to optimise the static icons under war/src/main/webapp/images/svgs.
//
// Re-run with:
//   npx svgo -r -f war/src/main/webapp/images/svgs --exclude "go-down.svg" --exclude "go-up.svg"
//
// These files were exported from Inkscape and carried editor metadata, <metadata> blocks and
// page-sized dimensions, which roughly doubled their weight in jenkins.war.
//
// go-down.svg and go-up.svg are excluded because they are sprites: their only content is a
// <symbol id="arrow"> with no <use> in the same file, so svgo reasonably concludes the markup is
// unreachable and reduces them to an empty <svg/>. Rendering them standalone produces nothing
// either way, which means a visual comparison cannot tell the two apart -- so leave them alone
// rather than rely on one.
//
// removeViewBox is not part of preset-default in svgo 4, so viewBox survives without naming it.
module.exports = {
  multipass: true,
  plugins: [
    {
      name: "preset-default",
      params: {
        overrides: {
          // Ids can be referenced from outside the file, so keep the names they were given.
          cleanupIds: false,
        },
      },
    },
  ],
};
