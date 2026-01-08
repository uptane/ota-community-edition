const gulp = require('gulp');
const exec = require('child_process').exec;
var tar = require('gulp-tar');
var gzip = require('gulp-gzip');

const getDockerTag = (version) => {
  const package = require('./package.json');
  const dockerTagTemplate = package.dockerTagTemplate + (process.env.DOCKER_TAG_SUFFIX || '');
  const dockerTag = dockerTagTemplate.replace('{name}', package.name).replace('{version}', version || package.version);
  return dockerTag;
};
const getDockerGuiTag = (version) => {
  const package = require('./package.json');
  const dockerTagTemplate = package.dockerGUITagTemplate + (process.env.DOCKER_TAG_SUFFIX || '');
  const dockerTag = dockerTagTemplate.replace('{name}', package.name).replace('{version}', version || package.version);
  return dockerTag;
};

gulp.task('electron:compress', function(cb) {
  const dirs = [{ src: 'Torizon OTA Admin-darwin*/**', dest: 'macOS' }, { src: 'Torizon OTA Admin-linux*/**', dest: 'linux' }, { src: 'Torizon OTA Admin-win*/**', dest: 'windows' }];
  // const dirs = [
  //     'Torizon OTA Admin-darwin-x64',
  //     'Torizon OTA Admin-linux-arm64',
  //     'Torizon OTA Admin-linux-armv7l',
  //     'Torizon OTA Admin-linux-ia32',
  //     'Torizon OTA Admin-linux-x64',
  //     'Torizon OTA Admin-win32-ia32',
  //     'Torizon OTA Admin-win32-x64',
  // ];
  const done = (i) => {
    if (i === dirs.length - 1) {
      cb();
    }
  };
  dirs.forEach((p, i) => {
    gulp
      .src('./dist/electron-out/' + p.src)
      .pipe(tar('Torizon-OTA-Admin.tar'))
      .pipe(gzip({ skipGrowingFiles: true }))
      .pipe(gulp.dest('./dist/packages/' + p.dest.replace(/\s/g, '_')))
      .on('finish', () => {
        done(i);
      });
  });
});

gulp.task('electron:copy', (cb) => {
  exec(`cp -r "./dist/electron-out/Torizon OTA Admin-linux-armv7l"  ./electron-release/linux-armv7l`, (err2, stdout2, stderr2) => {
    console.log(stdout2);
    console.error(stderr2);
    cb(err2);
  });
});
gulp.task('docker:build', (cb) => {
  const dockerTag = getDockerTag();
  console.info(`Building docker image with tag ${dockerTag}`);
  exec(`docker build -t ${dockerTag} .`, (err2, stdout2, stderr2) => {
    console.log(stdout2);
    console.error(stderr2);
    cb(err2);
  });
});
gulp.task('docker:push', (cb) => {
  const dockerTagVersion = getDockerTag();
  const dockerTagLatest = getDockerTag('latest');
  console.info(`Pushing docker image with tag ${dockerTagVersion} and  ${dockerTagLatest}`);
  exec(`docker push ${dockerTagVersion}; docker push ${dockerTagLatest}`, (err2, stdout2, stderr2) => {
    console.log(stdout2);
    console.error(stderr2);
    cb(err2);
  });
});
gulp.task('docker:tag', (cb) => {
  const dockerTagVersion = getDockerTag();
  const dockerTagLatest = getDockerTag('latest');
  console.info(`Tagging image as latest from tag ${dockerTagVersion}`);
  exec(`docker tag ${dockerTagVersion} ${dockerTagLatest}`, (err2, stdout2, stderr2) => {
    console.log(stdout2);
    console.error(stderr2);
    cb(err2);
  });
});
gulp.task('docker:gui:push', (cb) => {
  const dockerTagVersion = getDockerGuiTag();
  const dockerTagLatest = getDockerGuiTag('latest');
  console.info(`Pushing docker GUI image with tag ${dockerTagVersion} and  ${dockerTagLatest}`);
  exec(`docker push ${dockerTagVersion}; docker push ${dockerTagLatest}`, (err2, stdout2, stderr2) => {
    console.log(stdout2);
    console.error(stderr2);
    cb(err2);
  });
});
gulp.task('docker:gui:tag', (cb) => {
  const dockerTagVersion = getDockerGuiTag();
  const dockerTagLatest = getDockerGuiTag('latest');
  console.info(`Tagging docker GUI image as latest from tag ${dockerTagVersion}`);
  exec(`docker tag ${dockerTagVersion} ${dockerTagLatest}`, (err2, stdout2, stderr2) => {
    console.log(stdout2);
    console.error(stderr2);
    cb(err2);
  });
});

gulp.task('docker:gui:build', (cb) => {
  const dockerTag = getDockerGuiTag();
  console.info(`Building docker GUI image with tag ${dockerTag}`);
  exec(`docker build -f gui.Dockerfile -t ${dockerTag} .`, (err2, stdout2, stderr2) => {
    console.log(stdout2);
    console.error(stderr2);
    cb(err2);
  });
});
