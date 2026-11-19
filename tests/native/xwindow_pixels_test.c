#include <assert.h>
#include <stdio.h>
#include <string.h>
#include "../../base/com/android/XWindow.c"

/* Match the portable XPutPixel oracle across layouts, byte orders and padding. */
int main(void) {
    const jint rgb[] = {0xff123456, 0x00765432, 0xffffffff, 0, 0xff0000, 0x00ff00, 0x0000ff,
        0x01020304, 0x00808080, 0xabcdef, 0x13579b, 0x2468ac, 0xffffff, 0x987654,
        0x112233, 0x445566, 0x778899, 0xaabbcc, 0xddeeff, 0x111111, 0xeeeeee};
    for (int bits = 16; bits <= 32; bits += 8) for (int order = LSBFirst; order <= MSBFirst; order++) {
        XImage image = {0};image.width=7;image.height=3;image.format=ZPixmap;
        image.byte_order=order;image.bitmap_bit_order=order;image.bitmap_unit=32;image.bitmap_pad=32;
        image.depth=bits==32?24:bits;image.bits_per_pixel=bits;image.bytes_per_line=bits==32?40:bits==24?28:20;
        image.red_mask=bits==16?0xf800:0xff0000;image.green_mask=bits==16?0x7e0:0xff00;image.blue_mask=bits==16?0x1f:0xff;
        assert(XInitImage(&image));size_t bytes=(size_t)image.bytes_per_line*image.height;
        image.data=calloc(1,bytes);assert(image.data);XImage reference=image;reference.data=calloc(1,bytes);assert(reference.data);
        copy_frame_pixels(&image,rgb,7,3);
        for(int y=0;y<3;y++)for(int x=0;x<7;x++){unsigned int value=(unsigned int)rgb[y*7+x];XPutPixel(&reference,x,y,component(value>>16&255,image.red_mask)|component(value>>8&255,image.green_mask)|component(value&255,image.blue_mask));}
        assert(memcmp(image.data,reference.data,bytes)==0);free(image.data);free(reference.data);
    }
    puts("PASS: native pixel conversion matches XPutPixel for 16/24/32-bit images, both byte orders and padded rows");
    return 0;
}
