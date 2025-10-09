%Tommy Entwisle
%ME488
%10/6/25

clear; clc; close all;

dataFile = 'tensile_test_data.csv'; %set path to .csv if script isnt in the same folder

T = readtable(dataFile);

reqVars = {'Load','Elongation','Failure_Mode','Area'};
missing = setdiff(reqVars, T.Properties.VariableNames);
if ~isempty(missing)
    error('Missing required columns: %s', strjoin(missing, ', '));
end

T.Stress_MPa = T.Load ./ T.Area;

[datadir,~,~] = fileparts(which(dataFile));
if isempty(datadir)
    [datadir,~,~] = fileparts(dataFile);
end
if isempty(datadir)
    datadir = pwd;  
end
%% outputting new .csv with the column for stress (not sure if thats what the instructions meant)
outName = 'tensile_test_data_with_stress.csv';
outPathPrimary = fullfile(datadir, outName);

savedOK = false;
try
    writetable(T, outPathPrimary);
    fprintf('saved: %s\n\n', outPathPrimary);
    savedOK = true;
catch ME
    fprintf('save failed (%s).\n', ME.message);
end

if ~savedOK
    outPathTemp = fullfile(tempdir, outName);
    try
        writetable(T, outPathTemp);
        fprintf('saved to temp: %s\n\n', outPathTemp);
        savedOK = true;
    catch ME
        fprintf('save failed (%s).\n', ME.message);
    end
end

if ~savedOK
    
    if ispc
        desktopDir = fullfile(getenv('USERPROFILE'), 'Desktop');
    else
        desktopDir = fullfile(getenv('HOME'), 'Desktop');
    end
    if ~exist(desktopDir,'dir'); mkdir(desktopDir); end
    outPathDesk = fullfile(desktopDir, outName);
    try
        writetable(T, outPathDesk);
        fprintf('saved: %s\n\n', outPathDesk);
        savedOK = true;
    catch ME
        error('error: %s', ME.message);
    end
end
%%

%classifications:

%load (N)                         continuous quantitative
%elongation (mm)                  continuous quantitative
%failure mode (brittle, ductile)  categorical qualitative
%area (mm^2)                      continuous quantitative
%stress (MPa)                     continuous quantitative
 

meanStress   = mean(T.Stress_MPa);
medianStress = median(T.Stress_MPa);
varStress    = var(T.Stress_MPa, 0);  
stdStress    = std(T.Stress_MPa, 0);  

fprintf('Stress Result (MPa)\n');
fprintf('  Mean:             %.3f\n', meanStress);
fprintf('  Median:           %.3f\n', medianStress);
fprintf('  Variance (MPa^2): %.3f\n', varStress);
fprintf('  Std. Deviation:   %.3f\n\n', stdStress);

%histogram
figure;
histogram(T.Stress_MPa, 10, 'EdgeColor','k');
title('Histogram of Stress at Failure');
xlabel('Stress (MPa)'); ylabel('Frequency'); grid on;

%single-dist box plot
figure;
boxchart(ones(height(T),1), T.Stress_MPa);
xlim([0.5 1.5]);
set(gca,'XTick',1,'XTickLabel',{'Stress (MPa)'});
title('Stress at Failure Box Plot');
ylabel('Stress (MPa)'); grid on;

%box plot by failure mode
if ~iscategorical(T.Failure_Mode)
    T.Failure_Mode = categorical(T.Failure_Mode);
end
figure;
boxchart(T.Failure_Mode, T.Stress_MPa);
title('Stress Distribution by Failure Mode');
xlabel('Failure Mode'); ylabel('Stress (MPa)'); grid on;