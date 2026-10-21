function report = run_symbolic_regression(beforeDirectory, afterDirectory, reportFile, selectedCases)
% Compare actual pre-fix and fixed generated functions, with Symbolic Math Toolbox.
% JUnit fixture export: -Dgaalop.matlab.fixtureDir=... -Dgaalop.matlab.fixtureSuffix=_before/_after
    if nargin < 3, reportFile = ''; end
    if nargin < 4, selectedCases = {}; end
    addpath(beforeDirectory, afterDirectory);
    files = dir(fullfile(afterDirectory, '*_after.m'));
    report = struct('matlabVersion', version, 'numericComparisons', 0, ...
        'symbolicComparisons', 0, 'originalSymbolicFailures', 0, 'cases', {{}});
    for fileIndex = 1:numel(files)
        name = erase(files(fileIndex).name, '_after.m');
        if ~isempty(selectedCases) && ~ismember(name, selectedCases), continue; end
        oldCode = fileread(fullfile(beforeDirectory, [name '_before.m']));
        newCode = fileread(fullfile(afterDirectory, files(fileIndex).name));
        [oldFunction, oldInputs, oldOutputs] = signature(oldCode);
        [newFunction, inputs, outputs] = signature(newCode);
        assert(isequal(inputs, oldInputs) && isequal(outputs, oldOutputs), 'Signature changed: %s', name);
        count = numel(inputs);
        profiles = 1:6;
        if count == 0, profiles = 1; end
        for profile = profiles
            arguments = numericArguments(count, profile);
            oldResults = invoke(oldFunction, arguments, numel(outputs));
            newResults = invoke(newFunction, arguments, numel(outputs));
            for k = 1:numel(outputs)
                assert(strcmp(class(oldResults{k}), class(newResults{k})), 'Numeric class changed: %s.%s', name, outputs{k});
                assert(isequal(size(oldResults{k}), size(newResults{k})), 'Numeric shape changed: %s.%s', name, outputs{k});
                assert(isequaln(oldResults{k}, newResults{k}), 'Numeric values changed: %s.%s', name, outputs{k});
            end
            if strcmp(name, 'intermediate')
                checkIntermediate(newResults, outputs, arguments, profile >= 5);
            end
            report.numericComparisons = report.numericComparisons + 1;
        end
        if count > 0
            masks = unique([eye(count); ones(1, count)], 'rows', 'stable');
            for maskIndex = 1:size(masks, 1)
                mask = logical(masks(maskIndex, :));
                arguments = numericArguments(count, 2);
                symbolicNames = cell(1, count);
                for k = 1:count, symbolicNames{k} = sym(sprintf('gaalopArgument%d', k)); end
                symbolicArguments = arguments;
                symbolicArguments(mask) = symbolicNames(mask);
                try
                    invoke(oldFunction, symbolicArguments, numel(outputs));
                catch originalFailure
                    assert(contains(originalFailure.message, "not convertible to 'double'") ...
                        || contains(originalFailure.message, "not convertible to 'single'"), originalFailure.message);
                    report.originalSymbolicFailures = report.originalSymbolicFailures + 1;
                end
                symbolicResults = invoke(newFunction, symbolicArguments, numel(outputs));
                numericResults = invoke(oldFunction, arguments, numel(outputs));
                for k = 1:numel(outputs)
                    value = symbolicResults{k};
                    assert(isequal(size(value), size(numericResults{k})), 'Symbolic shape changed: %s.%s', name, outputs{k});
                    if isa(value, 'sym')
                        value = double(subs(value, [symbolicNames{mask}], [arguments{mask}]));
                    end
                    close(value, numericResults{k}, 1e-10, [name '.' outputs{k}]);
                end
                if strcmp(name, 'independent')
                    assert(isa(symbolicResults{strcmp(outputs,'left')}, 'sym') == mask(strcmp(inputs,'alpha')));
                    assert(isa(symbolicResults{strcmp(outputs,'right')}, 'sym') == mask(strcmp(inputs,'beta')));
                end
                if strcmp(name, 'intermediate')
                    checkIntermediate(symbolicResults, outputs, symbolicArguments, false);
                end
                report.symbolicComparisons = report.symbolicComparisons + 1;
                % Check a different substitution, including complex values, when
                % all parameters remain symbolic in this invocation.
                if all(mask)
                    for profile = [1, 4]
                        substitutions = numericArguments(count, profile);
                        expected = invoke(oldFunction, substitutions, numel(outputs));
                        for k = 1:numel(outputs)
                            value = symbolicResults{k};
                            if isa(value, 'sym'), value = double(subs(value, [symbolicNames{:}], [substitutions{:}])); end
                            close(value, expected{k}, 1e-10, [name ' complex/zero substitution']);
                        end
                        report.symbolicComparisons = report.symbolicComparisons + 1;
                    end
                end
            end
        end
        report.cases{end+1} = name;
        fprintf('MATLAB_PASS %s\n', name);
    end
    assert(~isempty(report.cases), 'No fixture pairs found');
    if ~isempty(reportFile)
        fileId = fopen(reportFile, 'w', 'n', 'UTF-8');
        assert(fileId >= 0, 'Cannot write report');
        cleanup = onCleanup(@() fclose(fileId));
        fprintf(fileId, '%s', jsonencode(report));
    end
    disp(jsonencode(report));
end

function [name, inputs, outputs] = signature(code)
    parts = regexp(code, 'function\s*\[([^\]]*)\]\s*=\s*(\w+)\(([^)]*)\)', 'tokens', 'once');
    assert(~isempty(parts), 'Missing generated function signature');
    name = parts{2};
    inputs = splitNames(parts{3});
    outputs = splitNames(parts{1});
end

function names = splitNames(text)
    if isempty(strtrim(text)), names = {}; else, names = strtrim(strsplit(text, ',')); end
end

function values = numericArguments(count, profile)
    values = cell(1, count);
    for k = 1:count
        realValues = [0.37, -0.5, 2, 1.25];
        switch profile
            case 1, value = 0;
            case 2, value = realValues(mod(k-1, 4)+1);
            case 3, value = (-1)^k * k / 4;
            case 4, value = complex(realValues(mod(k-1, 4)+1), k/8);
            case 5, value = single(realValues(mod(k-1, 4)+1));
            case 6
                value = realValues(mod(k-1, 4)+1);
                if mod(k,2), value = single(value); end
        end
        values{k} = value;
    end
end

function results = invoke(name, arguments, outputCount)
    results = cell(1, outputCount);
    if outputCount == 0, feval(name, arguments{:}); else, [results{:}] = feval(name, arguments{:}); end
end

function checkIntermediate(results, outputs, arguments, singlePrecision)
    alpha = arguments{1}; beta = arguments{2}; gamma = arguments{3};
    oracle.first = [2, 0, alpha*(beta+gamma)+gamma, 0, 0, 0, 1+alpha];
    oracle.second = [0, 3, 0, 0, alpha-beta*gamma];
    oracle.constant = [7, 0, 0, 0, 1];
    tolerance = 1e-10;
    if singlePrecision, tolerance = 1e-6; end
    for k = 1:numel(outputs)
        actual = results{k}; expected = oracle.(outputs{k});
        if isa(actual, 'sym') || isa(expected, 'sym')
            assert(all(isAlways(simplify(actual-expected) == 0)), 'Independent symbolic oracle failed');
        else
            close(actual, expected, tolerance, 'Independent intermediate-array oracle');
        end
    end
end

function close(actual, expected, tolerance, label)
    assert(isequal(size(actual), size(expected)), 'Shape mismatch: %s', label);
    assert(all(abs(double(actual(:))-double(expected(:))) <= tolerance*(1+abs(double(expected(:))))), 'Value mismatch: %s', label);
end
