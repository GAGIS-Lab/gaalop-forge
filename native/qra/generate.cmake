# A failed upstream generation leaves an incomplete directory. Never treat it as success.
if(EXISTS "${OUTPUT}")
  file(GLOB SAVED_CONFIGS "${OUTPUT}/input.conf")
  file(GLOB HEADERS "${OUTPUT}/src/*/Mvec.hpp")
  list(LENGTH SAVED_CONFIGS CONFIG_COUNT)
  if(NOT CONFIG_COUNT EQUAL 1 OR NOT HEADERS)
    message(FATAL_ERROR "Incomplete generation: ${OUTPUT}. Use a fresh build directory.")
  endif()
  list(GET SAVED_CONFIGS 0 SAVED_CONFIG)
  file(SHA256 "${CONFIG}" REQUESTED_HASH)
  file(SHA256 "${SAVED_CONFIG}" SAVED_HASH)
  if(NOT REQUESTED_HASH STREQUAL SAVED_HASH)
    message(FATAL_ERROR "Configuration changed: use a fresh build directory.")
  endif()
else()
  execute_process(COMMAND "${GENERATOR}" "${CONFIG}" RESULT_VARIABLE RESULT)
  if(NOT RESULT EQUAL 0)
    message(FATAL_ERROR "QRA generation failed (${RESULT})")
  endif()
  configure_file("${CONFIG}" "${OUTPUT}/input.conf" COPYONLY)
endif()
# Upstream emits a giant initializer-list for dualPermutations. At 20 dimensions
# its temporary arrays overflow the default Windows loader thread stack, causing
# DLL initialization error 0xc0000142. Construct the same mapping on the heap.
file(GLOB CONSTANT_HEADERS "${OUTPUT}/src/*/Constants.hpp")
foreach(HEADER IN LISTS CONSTANT_HEADERS)
  file(READ "${HEADER}" CONTENT)
  string(REGEX REPLACE "std::array<std::vector<unsigned int>, [0-9]+> dualPermutations = [^\n]+"
    "std::array<std::vector<unsigned int>, algebraDimension + 1> dualPermutations = [] { std::array<std::vector<unsigned int>, algebraDimension + 1> values; for(unsigned g=0;g<=algebraDimension;++g) values[g].resize(binomialArray[g]); for(unsigned m=0;m<(1u<<algebraDimension);++m) values[xorIndexToGrade[m]][xorIndexToHomogeneousIndex[m]]=xorIndexToHomogeneousIndex[((1u<<algebraDimension)-1)^m]; return values; }();"
    CONTENT "${CONTENT}")
  file(WRITE "${HEADER}" "${CONTENT}")
endforeach()
