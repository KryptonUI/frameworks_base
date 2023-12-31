/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <android/util/EncodedBuffer.h>
#include <android/util/protobuf.h>

#include <stdlib.h>

namespace android {
namespace util {

const size_t BUFFER_SIZE = 8 * 1024; // 8 KB

EncodedBuffer::Pointer::Pointer() : Pointer(BUFFER_SIZE)
{
}

EncodedBuffer::Pointer::Pointer(size_t chunkSize)
        :mIndex(0),
         mOffset(0)
{
    mChunkSize = chunkSize == 0 ? BUFFER_SIZE : chunkSize;
}

size_t
EncodedBuffer::Pointer::pos() const
{
    return mIndex * mChunkSize + mOffset;
}

size_t
EncodedBuffer::Pointer::index() const
{
    return mIndex;
}

size_t
EncodedBuffer::Pointer::offset() const
{
    return mOffset;
}

EncodedBuffer::Pointer*
EncodedBuffer::Pointer::move(size_t amt)
{
    size_t newOffset = mOffset + amt;
    mIndex += newOffset / mChunkSize;
    mOffset = newOffset % mChunkSize;
    return this;
}

EncodedBuffer::Pointer*
EncodedBuffer::Pointer::rewind()
{
    mIndex = 0;
    mOffset = 0;
    return this;
}

EncodedBuffer::Pointer
EncodedBuffer::Pointer::copy() const
{
    Pointer p = Pointer(mChunkSize);
    p.mIndex = mIndex;
    p.mOffset = mOffset;
    return p;
}

// ===========================================================
EncodedBuffer::EncodedBuffer() : EncodedBuffer(0)
{
}

EncodedBuffer::EncodedBuffer(size_t chunkSize)
        :mBuffers()
{
    mChunkSize = chunkSize == 0 ? BUFFER_SIZE : chunkSize;
    mWp = Pointer(mChunkSize);
    mEp = Pointer(mChunkSize);
}

EncodedBuffer::~EncodedBuffer()
{
    for (size_t i=0; i<mBuffers.size(); i++) {
        uint8_t* buf = mBuffers[i];
        free(buf);
    }
}

inline uint8_t*
EncodedBuffer::at(const Pointer& p) const
{
    return mBuffers[p.index()] + p.offset();
}

void
EncodedBuffer::clear()
{
    mWp.rewind();
    mEp.rewind();
}

/******************************** Write APIs ************************************************/
size_t
EncodedBuffer::size() const
{
    return mWp.pos();
}

EncodedBuffer::Pointer*
EncodedBuffer::wp()
{
    return &mWp;
}

uint8_t*
EncodedBuffer::writeBuffer()
{
    // This prevents write pointer move too fast than allocating the buffer.
    if (mWp.index() > mBuffers.size()) return NULL;
    uint8_t* buf = NULL;
    if (mWp.index() == mBuffers.size()) {
        buf = (uint8_t*)malloc(mChunkSize);

        if (buf == NULL) return NULL; // This indicates NO_MEMORY

        mBuffers.push_back(buf);
    }
    return at(mWp);
}

size_t
EncodedBuffer::currentToWrite()
{
    return mChunkSize - mWp.offset();
}

void
EncodedBuffer::writeRawByte(uint8_t val)
{
    *writeBuffer() = val;
    mWp.move();
}

size_t
EncodedBuffer::writeRawVarint64(uint64_t val)
{
    size_t size = 0;
    while (true) {
        size++;
        if ((val & ~0x7F) == 0) {
            writeRawByte((uint8_t) val);
            return size;
        } else {
            writeRawByte((uint8_t)((val & 0x7F) | 0x80));
            val >>= 7;
        }
    }
}

size_t
EncodedBuffer::writeRawVarint32(uint32_t val)
{
    uint64_t v =(uint64_t)val;
    return writeRawVarint64(v);
}

void
EncodedBuffer::writeRawFixed32(uint32_t val)
{
    writeRawByte((uint8_t) val);
    writeRawByte((uint8_t) (val>>8));
    writeRawByte((uint8_t) (val>>16));
    writeRawByte((uint8_t) (val>>24));
}

void
EncodedBuffer::writeRawFixed64(uint64_t val)
{
    writeRawByte((uint8_t) val);
    writeRawByte((uint8_t) (val>>8));
    writeRawByte((uint8_t) (val>>16));
    writeRawByte((uint8_t) (val>>24));
    writeRawByte((uint8_t) (val>>32));
    writeRawByte((uint8_t) (val>>40));
    writeRawByte((uint8_t) (val>>48));
    writeRawByte((uint8_t) (val>>56));
}

size_t
EncodedBuffer::writeHeader(uint32_t fieldId, uint8_t wireType)
{
    return writeRawVarint32((fieldId << FIELD_ID_SHIFT) | wireType);
}

/******************************** Edit APIs ************************************************/
EncodedBuffer::Pointer*
EncodedBuffer::ep()
{
    return &mEp;
}

uint8_t
EncodedBuffer::readRawByte()
{
    uint8_t val = *at(mEp);
    mEp.move();
    return val;
}

uint64_t
EncodedBuffer::readRawVarint()
{
    uint64_t val = 0, shift = 0;
    size_t start = mEp.pos();
    while (true) {
        uint8_t byte = readRawByte();
        val += (byte & 0x7F) << shift;
        if ((byte & 0x80) == 0) break;
        shift += 7;
    }
    return val;
}

uint32_t
EncodedBuffer::readRawFixed32()
{
    uint32_t val = 0;
    for (auto i=0; i<32; i+=8) {
        val += (uint32_t)readRawByte() << i;
    }
    return val;
}

uint64_t
EncodedBuffer::readRawFixed64()
{
    uint64_t val = 0;
    for (auto i=0; i<64; i+=8) {
        val += (uint64_t)readRawByte() << i;
    }
    return val;
}

void
EncodedBuffer::editRawFixed32(size_t pos, uint32_t val)
{
    size_t oldPos = mEp.pos();
    mEp.rewind()->move(pos);
    for (auto i=0; i<32; i+=8) {
        *at(mEp) = (uint8_t) (val >> i);
        mEp.move();
    }
    mEp.rewind()->move(oldPos);
}

void
EncodedBuffer::copy(size_t srcPos, size_t size)
{
    if (size == 0) return;
    Pointer cp(mChunkSize);
    cp.move(srcPos);

    while (cp.pos() < srcPos + size) {
        writeRawByte(*at(cp));
        cp.move();
    }
}

/********************************* Read APIs ************************************************/
EncodedBuffer::iterator
EncodedBuffer::begin() const
{
    return EncodedBuffer::iterator(*this);
}

EncodedBuffer::iterator::iterator(const EncodedBuffer& buffer)
        :mData(buffer),
         mRp(buffer.mChunkSize)
{
}

size_t
EncodedBuffer::iterator::size() const
{
    return mData.size();
}

size_t
EncodedBuffer::iterator::bytesRead() const
{
    return mRp.pos();
}

EncodedBuffer::Pointer*
EncodedBuffer::iterator::rp()
{
    return &mRp;
}

uint8_t const*
EncodedBuffer::iterator::readBuffer()
{
    return hasNext() ? const_cast<uint8_t const*>(mData.at(mRp)) : NULL;
}

size_t
EncodedBuffer::iterator::currentToRead()
{
    return (mData.mWp.index() > mRp.index()) ?
            mData.mChunkSize - mRp.offset() :
            mData.mWp.offset() - mRp.offset();
}

bool
EncodedBuffer::iterator::hasNext()
{
    return mRp.pos() < mData.mWp.pos();
}

uint8_t
EncodedBuffer::iterator::next()
{
    uint8_t res = *(mData.at(mRp));
    mRp.move();
    return res;
}

uint64_t
EncodedBuffer::iterator::readRawVarint()
{
    uint64_t val = 0, shift = 0;
    while (true) {
        uint8_t byte = next();
        val += (byte & 0x7F) << shift;
        if ((byte & 0x80) == 0) break;
        shift += 7;
    }
    return val;
}

} // util
} // android
