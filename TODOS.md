# TODO List

## Completed Tasks ✅

- [x] **Fix adaptive bitrate bandwidth ordering at proxy level (runtime fix)**
- [x] **Implement auto-incrementing unique slugs for duplicate titles**
- [x] **Update API documentation to clarify title field purpose**
- [x] **Integrate CleanDirectory helper to clean hls-v2 and upload-v2 after job completion**
- [x] **Fix bandwidth ordering at encoding level (v3->v0 ascending)**
- [x] **Remove complex ordering logic from ProxyController**
- [x] **Add endpoint to delete all content from R2 and database**
- [x] **Fix delete-all endpoint to actually delete R2 storage files**
- [x] **Implement audio encoding with consistent structure (v0 variant + master playlist)**

## Pending Tasks ⏳

- [ ] **Test ABR ordering and unique slug generation**
- [ ] **Test audio encoding and upload functionality**

## In Progress 🚧

*None currently*

## Notes

- Audio encoding now creates the same structure as video: `master.m3u8` + `v0/index.m3u8` + `.ts` segments
- Audio files will be processed with 128kbps AAC encoding and proper HLS structure
- The system automatically detects audio-only files and processes them accordingly



