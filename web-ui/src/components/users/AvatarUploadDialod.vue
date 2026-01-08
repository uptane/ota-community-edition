<template>
  <div>
    <q-card-section>
      <div class="text-h6">Update Your Avatar</div>
    </q-card-section>

    <div v-if="loading" style="width: 200px; height: 200px;" class="bg-blue-grey flex flex-center text-h5">
      Saving image...
    </div>
    <div v-else>
      <vue-croppie v-if="imageUrl" class="bg-grey" style="width: 200px; height: 200px; margin-bottom: 3rem;" :viewport="{ width: 100, height: 100, type: 'circle' }" ref="croppieRef" :enableResize="false" @result="result" @update="update"></vue-croppie>
      <label
        v-if="!imageUrl"
        for="file"
        @dragover="
          draggingOver = true;
          $event.preventDefault();
        "
        @dragenter="draggingOver = true"
        @dragleave="draggingOver = false"
        @dragend="draggingOver = false"
        @drop="drop($event)"
      >
        <div
          class="flex flex-center"
          style="width: 200px; height: 200px;"
          :class="{
            'bg-primary ': draggingOver,
            'bg-secondary ': droppedIt,
            'bg-blue-grey ': !draggingOver && !droppedFile,
          }"
          :style="{
            'background-image': 'url(' + bgImage + ')',
          }"
        >
          <div class="text-center w-100 pt-1 pb-1" style="background-color: rgba(0,0,0, 0.2); cursor:pointer">
            <input id="file" accept="image/jpeg, image/svg+xml, image/png" class="d-none" type="file" name="files[]" @click="fileSelectorClicked($event)" @change="onFileSelected($event)" />
            <div>
              <div v-if="!draggingOver">
                <span class="text-bold text-2">Drag and drop</span>
                <br />or click to attach an image <br /><small>Accepts .jpg, .png and .svg</small>
                <br />
              </div>
              <div v-if="draggingOver">
                <span class="text-bold text-2">
                  <q-icon name="thumb_up" />
                </span>
                <br />OK to drop it
              </div>
              <div v-if="droppedIt">
                <span class="text-bold text-2">
                  <q-icon name="thumb_up" />
                </span>
                <br />Got it
              </div>
            </div>
          </div>
        </div>
      </label>

      <div class="text-secondary">
        <q-btn @click="onCancel" flat label="Cancel" color="grey" />
        <q-btn v-if="imageUrl" @click="onOk" flat label="Save changes" />
      </div>
    </div>
  </div>
</template>

<script>
import 'croppie/croppie.css';
import { mapActions, mapGetters } from 'vuex';
export default {
  name: 'AvatarUploader',
  data() {
    return {
      show: true,
      profileImage: '',
      imageUrl: '',
      invalidFile: false,
      resolvingFile: false,
      droppedFile: false,
      draggingOver: false,
      droppedIt: false,
      readyToDropFile: false,
      updatingProfile: false,
      loading: false,
    };
  },
  computed: {
    ...mapGetters({
      userData: 'users/userData',
    }),
    user() {
      return this.$store.getters['ui/user'] || {};
    },
    bgImage() {
      return this.imageUrl || this.userData.avatar;
    },
  },
  methods: {
    ...mapActions({
      saveAvatar: 'users/saveAvatar',
    }),
    bind() {
      this.$refs.croppieRef.bind({
        url: this.imageUrl,
      });
    },
    onOk(e) {
      this.loading = true;
      let options = {
        format: 'jpeg',
        circle: true,
      };
      this.$refs.croppieRef.result(options, (output) => {
        this.resizeImage(output, (blob, dataUrl) => {
          this.profileImage = dataUrl;
          this.updatingProfile = true;
          this.loading = true;
          // S3Service.uploadAvatar(this.profileImage)
          this.saveAvatar(this.profileImage)
            .then((a) => {
              this.updatingProfile = false;
              this.$emit('on-ok', { image: this.profileImage });
              this.imageUrl = '';
              this.loading = false;
              this.resetUploader();
            })
            .catch((e) => {
              this.loading = false;
            });
        });
      });
    },
    onCancel() {
      this.$emit('on-cancel', {});
    },

    allowDrop(ev) {
      ev.preventDefault();
    },

    drop(ev) {
      ev.preventDefault();
      ev.stopPropagation();
      this.droppedIt = true;
      this.draggingOver = false;
      const droppedFiles = ev.target.files || ev.dataTransfer.files || ev.originalEvent.target.files || ev.originalEvent.dataTransfer.files;
      this.readFile(droppedFiles[0]);
    },
    readFile(file) {
      this.resolvingFile = true;
      const isImage = file.type === 'image/jpeg' || file.type === 'image/svg+xml' || file.type === 'image/png';
      if (!isImage) {
        this.resetUploader();
        this.invalidFile = true;
        return;
      }
      const reader = new FileReader();
      reader.onload = (event) => {
        this.resetUploader();
        this.droppedFile = true;
        this.imageUrl = event.target.result;
        setTimeout(() => {
          this.resetUploader();
          this.bind();
        }, 1000);
      };
      reader.readAsDataURL(file);
    },
    onFileSelected(e) {
      this.readFile(e.target.files[0]);
    },
    fileSelectorClicked(e) {
      e.target.value = null;
    },
    result(output) {
      this.profileImage = output;
    },
    update(val) {},
    resetUploader() {
      this.invalidFile = false;
      this.droppedFile = false;
      this.resolvingFile = false;
      this.readyToDropFile = false;
    },
    resizeImage(imageUrl, cb) {
      var img = document.createElement('img');
      img.src = imageUrl;
      setTimeout(() => {
        var canvas = document.createElement('canvas');
        var ctx = canvas.getContext('2d');

        ctx.drawImage(img, 0, 0);

        var MAX_WIDTH = 150;
        var MAX_HEIGHT = 150;
        var width = img.width;
        var height = img.height;

        if (width > height) {
          if (width > MAX_WIDTH) {
            height *= MAX_WIDTH / width;
            width = MAX_WIDTH;
          }
        } else {
          if (height > MAX_HEIGHT) {
            width *= MAX_HEIGHT / height;
            height = MAX_HEIGHT;
          }
        }
        canvas.width = width;
        canvas.height = height;
        var ctx = canvas.getContext('2d');
        ctx.drawImage(img, 0, 0, width, height);
        canvas.toBlob(
          (blob) => {
            cb(blob, canvas.toDataURL('image/jpeg', 0.75));
          },
          'image/jpeg',
          0.8,
        );
      }, 1000);
    },
  },
};
</script>
