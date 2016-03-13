#ifndef ATTRIBUTE_H
#define ATTRIBUTE_H

#define BOOST_LOG_DYN_LINK 1

#include <boost/lexical_cast.hpp>
//#include <boost/type_index.hpp>
#include <cstdio>
#include <stdint.h>
#include <assert.h>
#include <iostream>
#include <fstream>
#include "endianness.h"

#define __STDC_FORMAT_MACROS
#include <inttypes.h>

#include <boost/log/core.hpp>
#include <boost/log/trivial.hpp>
#include <boost/log/expressions.hpp>

#include "buffer.h"

namespace logging = boost::log;

class Attribute {

 protected:
  // can the argument be a NULL value
  bool isNullable=false;

  // how many bytes the attribute requires
  int32_t bytesNumber; // this is only the number of real values (without any NULL values, for example, for char*)

  // is this instance of the argument a NULL value or not
  bool isNull;

  // null values have to have null char '\0' on each position
  static const int32_t nullValuesSize=20;
  // this is the declaration of null(s) for values that are empty (for example in SciDB binary format) - this is a string with only '\0' (null) values
  static const char nullValues[nullValuesSize];

 public:
  virtual Attribute* clone() const = 0;
  virtual char type() = 0;
  virtual ~Attribute() = 0;

  void setIsNullable(bool isNullable) { this->isNullable = isNullable; std::cout << "isNullable: " << this->isNullable;}
  bool getIsNullable() { return this->isNullable; }

  void setBytesNumber(int32_t bytesNumber) { this->bytesNumber = bytesNumber; }
  int32_t getBytesNumber() { return this->bytesNumber; }
  
  void setIsNull(bool isNull) { this->isNull = isNull; }
  bool getIsNull() { return this->isNull; }

  int virtual postgresReadBinary(FILE *fp) = 0;
  int virtual postgresReadBinaryBuffer(Buffer * buffer) = 0;
  //int virtual writeCsv(FILE *fp) = 0;
  int virtual writeCsv(std::ofstream & file) = 0;
  int virtual scidbWriteBinary(FILE *fp) = 0;
  int virtual scidbReadBinary(FILE *fp) = 0;
  int virtual postgresWriteBinary(FILE *fp) = 0; 
};

Attribute* new_clone(Attribute const& other);

template <class T>
class GenericAttribute : public Attribute
{
 private:
  T value;
 public:
  GenericAttribute(bool isNullable=false);
  ~GenericAttribute() {}
  GenericAttribute<T>* clone() const{return new GenericAttribute<T>(*this);}
  char type() { return 1;}
  void setValue(T value) { this->value=value;}
  T getValue() {return this->value;}
  int postgresReadBinary(FILE *fp);
  int postgresReadBinaryBuffer(Buffer * buffer);
  int writeCsv(std::ofstream & ofile);
  int scidbWriteBinary(FILE *fp);
  int scidbReadBinary(FILE *fp);
  int postgresWriteBinary(FILE *fp);
};

// template specialization for strings (implemented as char*)

template<>
class GenericAttribute<char*> : public Attribute
{
 private:
  char* value;
  void freeValue();
 public:
  GenericAttribute(bool isNullable=false);
  ~GenericAttribute();
  GenericAttribute<char*>* clone() const { return new GenericAttribute<char*>(*this);}
  char type() { return 2;}
  void setValue(char* value) { this->value=value;}
  char* getValue() {return this->value;}
  int postgresReadBinary(FILE *fp);
  int postgresReadBinaryBuffer(Buffer * buffer);
  int writeCsv(std::ofstream & ofile);
  int scidbWriteBinary(FILE *fp);
  int scidbReadBinary(FILE *fp);
  int postgresWriteBinary(FILE *fp);
};

template <class T>
GenericAttribute<T>::GenericAttribute(bool isNullable)
{
  this->isNullable=isNullable;
  this->bytesNumber=sizeof(T);
}

template <class T>
int GenericAttribute<T>::postgresReadBinary(FILE *fp)
{
  //std::cout << "this is type: " << boost::typeindex::type_id().pretty_name() << std::endl;
  int32_t valueBytesNumber;
  fread(&valueBytesNumber,4,1,fp);
  //std::cout << "in postgres: value bytes number before endianness: " << std::oct << valueBytesNumber << std::endl;
  /* char buf[4]; */
  /* memcpy(buf,&valueBytesNumber,4); */
  /* printf("bytes postgres read for valueBytesNumber: "); */
  /* for (int i=0; i<4; ++i) { */
  /*   printf("%o\n",buf[i]); */
  /* } */
  //printf("%d\n",valueBytesNumber);
  //std::cout << "value bytes number before endianness: " << valueBytesNumber << std::endl;
  valueBytesNumber = endianness::from_postgres<int32_t>(valueBytesNumber);
  //std::cout << "value bytes number after endianness: " << valueBytesNumber << std::endl;
  /* char bufAfter[4]; */
  /* memcpy(bufAfter,&valueBytesNumber,4); */
  /* printf("bytes postgres read for valueBytesNumber: "); */
  /* for (int i=0; i<4; ++i) { */
  /*   printf("%o\n",bufAfter[i]); */
  /* } */
  /* printf("value bytes number int32_t:"); */
  /* printf("%" PRId32,valueBytesNumber); */
  /* printf("\n"); */
  if (valueBytesNumber == -1)
    {
      // this is null and there are no bytes for the value
      this->isNull=true;
      return 0;
    }
  this->isNull=false;
  /* std::cout << "value bytes number: " << valueBytesNumber << std::endl; */
  /* std::cout << "value size in bytes: " << sizeof(value) << std::endl; */
  /* std::cout << "this->bytesNumber: " << this->bytesNumber << std::endl; */
  assert(valueBytesNumber==this->bytesNumber);
  fread(&value,this->bytesNumber,1,fp);
  // change the endianness
  value = endianness::from_postgres<T>(value);
  /* char bufValue[100]; */
  /* memcpy(bufValue,&(this->value),this->bytesNumber); */
  /* printf("bytes postgres read for value: "); */
  /* for (int i=0; i<this->bytesNumber; ++i) { */
  /*   printf("%o\n",bufValue[i]); */
  /* } */
  //std::cout << "value read: " << value << std::endl;
  return 0; // success
}

template <class T>
int GenericAttribute<T>::scidbReadBinary(FILE *fp)
{
  //std::cout << "this is type: " << boost::typeindex::type_id().pretty_name() << std::endl;
  if(this->isNullable) 
    {
      /* read 1 byte that tells us if the attribute is null or not:
	 - If a nullable attribute contains a non-null value, 
	 the preceding null byte is -1.
	 - If a nullable attribute contains a null value, 
	 the preceding null byte will contain the missing reason code, 
	 which must be between 0 and 127
      */
      int8_t nullValue;
      size_t bytes_read;
      bytes_read = fread(&nullValue,1,1,fp);
      if(bytes_read < 1) return 1; // no more data in the input file
      if(nullValue>=0 && nullValue <= 127)
	{
	  this->isNull = true;
	  // we don't need the reason why it is null so we'll write byte 0
	  /* A fixed-length data type that allows null values 
	     will always consume one more byte than the datatype requires, 
	     regardless of whether the actual value is null or non-null. 
	     For example, an int8 will require 2 bytes and an int64 
	     will require 9 bytes. (In the figure, see bytes 2-4 or 17-19.)
	  */
	}
      else 
	{
	  /* if nullValue != -1: it means that there was another unexpected value 
	     different from [-1,127] */
	  assert(nullValue==-1);
	}
    }
  size_t elements_read = fread(&(this->value),this->bytesNumber,1,fp);
  //BOOST_LOG_TRIVIAL(debug) << "elements_read: " << elements_read;
  //BOOST_LOG_TRIVIAL(debug) << "bytes number in the attribute: " << this->bytesNumber;
  //BOOST_LOG_TRIVIAL(debug) << "value: " << this->value;
  if(elements_read != 1) return 1; // no more data in the input file
  return 0; // everything is correct: success
}

template <class T>
int GenericAttribute<T>::writeCsv(std::ofstream & ofile)
{
  // write nothing if the value is NULL
  if (this->isNull) return 0;
  // write the value only if it's not NULL
  // fprintf(fp,"%" PRId64,this->value);
  ofile << value;
  return 0;
}

template <class T>
int GenericAttribute<T>::scidbWriteBinary(FILE *fp) 
{
  if(this->isNullable) 
    {
      if(this->isNull)
	{
	  // we don't know the reason why it is null so we'll write byte 0
	  char nullReason=0;
	  fwrite(&nullReason,1,1,fp);
	  assert(nullValuesSize>=this->bytesNumber);
	  fwrite(nullValues,this->bytesNumber,1,fp);
	  return 0;
	}
      else 
	{
	  char notNull=-1;
	  fwrite(&notNull,1,1,fp);
	}
    }
  fwrite(&(this->value),this->bytesNumber,1,fp);
  return 0;
}

template <class T>
int GenericAttribute<T>::postgresWriteBinary(FILE *fp) 
{
  //BOOST_LOG_TRIVIAL(debug) << "postgresWriteBinary is nullable: " << this->isNullable;
  if(this->isNullable) 
    {
      if(this->isNull)
	{
	  /* -1 indicates a NULL field value */
	  int32_t nullValue = -1;
	  int32_t nullValuePostgres = endianness::to_postgres<int32_t>(nullValue);
	  fwrite(&nullValuePostgres,4,1,fp);
	  /* No value bytes follow in the NULL case. */
	  return 0;
	}
    }
  int32_t attrLengthPostgres = endianness::to_postgres<int32_t>(this->bytesNumber);
  fwrite(&attrLengthPostgres,4,1,fp);
  T valuePostgres = endianness::to_postgres<T>(this->value);
  //BOOST_LOG_TRIVIAL(debug) << "postgresWriteBinary bytes number: " << this->bytesNumber;
  fwrite(&valuePostgres,this->bytesNumber,1,fp);
  return 0;
}

template <class T>
int GenericAttribute<T>::postgresReadBinaryBuffer(Buffer * buffer)
{
  //fprintf(stderr,"%s\n","Read binary buffer");
  int32_t valueBytesNumber;
  BufferRead(&valueBytesNumber,4,1,buffer);
  valueBytesNumber = endianness::from_postgres<int32_t>(valueBytesNumber);
  //printf("type size read: %d\n",valueBytesNumber);
  //printf("expected type size: %d\n",this->bytesNumber);
  if (valueBytesNumber == -1)
    {
      // this is null and there are no bytes for the value
      this->isNull=true;
      return 0;
    }
  this->isNull=false;
  assert(valueBytesNumber==this->bytesNumber);
  BufferRead(&value,this->bytesNumber,1,buffer);
  value = endianness::from_postgres<T>(value);
  //std::cout << "value read: " << value << std::endl;
  return 0; // success
}

#endif // ATTRIBUTE_H


