//
//  Generated by the J2ObjC translator.  DO NOT EDIT!
//  source: /Users/ex3ndr/Develop/actor-model/library/actor-cocoa-base/build/java/im/actor/model/api/TextMessageEx.java
//

#ifndef _ImActorModelApiTextMessageEx_H_
#define _ImActorModelApiTextMessageEx_H_

@class IOSByteArray;

#include "J2ObjC_header.h"
#include "im/actor/model/droidkit/bser/BserObject.h"

@interface ImActorModelApiTextMessageEx : BSBserObject {
}

+ (ImActorModelApiTextMessageEx *)fromBytesWithByteArray:(IOSByteArray *)src;

- (jint)getHeader;

- (IOSByteArray *)buildContainer;

- (instancetype)init;

@end

J2OBJC_EMPTY_STATIC_INIT(ImActorModelApiTextMessageEx)

CF_EXTERN_C_BEGIN

FOUNDATION_EXPORT ImActorModelApiTextMessageEx *ImActorModelApiTextMessageEx_fromBytesWithByteArray_(IOSByteArray *src);
CF_EXTERN_C_END

J2OBJC_TYPE_LITERAL_HEADER(ImActorModelApiTextMessageEx)

#endif // _ImActorModelApiTextMessageEx_H_
