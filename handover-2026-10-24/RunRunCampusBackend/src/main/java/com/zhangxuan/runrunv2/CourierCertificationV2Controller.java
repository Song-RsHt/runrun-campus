package com.zhangxuan.runrunv2;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v2/courier/certification")
public class CourierCertificationV2Controller {
 private final IdentityService identity; private final CourierCertificationV2Service service;
 public CourierCertificationV2Controller(IdentityService identity,CourierCertificationV2Service service){this.identity=identity;this.service=service;}
 @IdentityPermission(role="COURIER") @GetMapping("/me")
 public CourierCertificationV2Service.Certification me(@RequestHeader("Authorization") String auth){var a=identity.authenticate(token(auth));return service.getMine(Long.parseLong(a.id()));}
 @IdentityPermission(role="COURIER") @PostMapping("/applications")
 public CourierCertificationV2Service.Certification apply(@RequestHeader("Authorization") String auth,@RequestHeader(value="Idempotency-Key",defaultValue="missing") String key){return service.apply(token(auth),identity,key);}
 @IdentityPermission(role="COURIER") @PostMapping(value="/applications/{id}/student-card",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
 public CourierCertificationV2Service.Certification upload(@RequestHeader("Authorization") String auth,@PathVariable long id,@RequestPart MultipartFile file,@RequestPart long expectedVersion,@RequestHeader(value="Idempotency-Key",defaultValue="missing") String key){return service.upload(token(auth),identity,id,expectedVersion,key,file);}
 @IdentityPermission(role="ADMIN") @PostMapping("/{id}/decision")
 public CourierCertificationV2Service.Certification decision(@RequestHeader("Authorization") String auth,@PathVariable long id,@RequestBody Decision body,@RequestHeader(value="Idempotency-Key",defaultValue="missing") String key){return service.decide(token(auth),identity,id,body.expectedVersion(),body.photoResourceId(),body.decision(),body.reason(),key);}
 @IdentityPermission(role="ADMIN") @GetMapping(value="/{id}/student-card",produces={"image/jpeg","image/png"})
 public ResponseEntity<byte[]> read(@RequestHeader("Authorization") String auth,@PathVariable long id,@RequestParam(required=false) String photoResourceId,@RequestHeader(value="X-Request-Id",defaultValue="read") String requestId){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma","no-cache").header("X-Content-Type-Options","nosniff").body(service.read(token(auth),identity,id,photoResourceId,requestId));}
 public record Decision(String decision,long expectedVersion,String photoResourceId,String reason){}
 private static String token(String h){if(h==null||!h.startsWith("Bearer "))throw IdentityFailure.auth();return h.substring(7);}
}
