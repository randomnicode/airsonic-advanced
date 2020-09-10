#!/bin/bash

set -e

echo "Process will run as:"
echo "User: $(id -u)"
echo "Group: $(id -g)"

# transcode folder
mkdir -p $AIRSONIC_DIR/airsonic/transcode
[[ ! -f $AIRSONIC_DIR/airsonic/transcode/ffmpeg ]] && ln -fs /usr/bin/ffmpeg $AIRSONIC_DIR/airsonic/transcode/ffmpeg
[[ ! -f $AIRSONIC_DIR/airsonic/transcode/lame ]] && ln -fs /usr/bin/lame $AIRSONIC_DIR/airsonic/transcode/lame

# symlinks to facilitate migration from old airsonic docker images (dbs will contain reference to /airsonic)
mkdir -p /airsonic
ln -fs $AIRSONIC_DIR/airsonic /airsonic/data
ln -fs $AIRSONIC_DIR/music /airsonic/music
ln -fs $AIRSONIC_DIR/podcasts /airsonic/podcasts
ln -fs $AIRSONIC_DIR/playlists /airsonic/playlists

java --version
echo "JAVA_OPTS=$JAVA_OPTS"
$AIRSONIC_DIR/airsonic/transcode/ffmpeg -version
curl --version
echo "PATH=$PATH"

if [[ $# -lt 1 ]] || [[ ! "$1" == "java"* ]]; then
    java_opts_array=()
    while IFS= read -r -d '' item; do
        java_opts_array+=( "$item" )
    done < <([[ $JAVA_OPTS ]] && xargs printf '%s\0' <<<"$JAVA_OPTS")
    exec java -Xmx256m \
     -Dserver.address=0.0.0.0 \
     -Dserver.port=$AIRSONIC_PORT \
     -Dserver.servlet.context-path=$CONTEXT_PATH \
     -Dairsonic.home=$AIRSONIC_DIR/airsonic \
     -Dairsonic.defaultMusicFolder=$AIRSONIC_DIR/music \
     -Dairsonic.defaultPodcastFolder=$AIRSONIC_DIR/podcasts \
     -Dairsonic.defaultPlaylistFolder=$AIRSONIC_DIR/playlists \
     -DUPnpPort=$UPNP_PORT \
     -Djava.awt.headless=true \
     "${java_opts_array[@]}" \
     -jar airsonic.war "$@"
fi

exec "$@"
