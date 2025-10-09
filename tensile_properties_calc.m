% MATLAB Script for Problem 2-6


P_elastic = [1000, 2000, 3000, 4000, 7000, 8400, 8800, 9200]; % Load in lbf
elongation = [0.0004, 0.0006, 0.0010, 0.0013, 0.0023, 0.0028, 0.0036, 0.0089]; %elongation in inches

P_plastic = [8800, 9200, 9100, 13200, 15200, 17000, 16400, 14800]; %load in lbf
area = [0.1984, 0.1978, 0.1963, 0.1924, 0.1875, 0.1563, 0.1307, 0.1077]; %area in square inches

diameter = 0.503; %initial diameter
gauge_length = 2; %gage length


initial_area = pi * (diameter/2)^2; %initial cross-sectional area

%Stress and strain
stress_elastic = P_elastic / initial_area; %stress elastic
strain_elastic = elongation / gauge_length; %strain elastic

stress_plastic = P_plastic / initial_area; %stress plastic
strain_plastic = (initial_area - area) / initial_area; %strain plastic

%plot for stress-strain 
figure;
hold on;
plot(strain_elastic, stress_elastic, '-o', 'DisplayName', 'Elastic Region');
plot(strain_plastic, stress_plastic, '-x', 'DisplayName', 'Plastic Region');
xlabel('Strain (mm/mm)');
ylabel('Stress (psi)');
title('Stress-Strain Plot (Steel)');
legend;
grid on;
hold off;

linear_idx = 1:4; %linearity in the first four data points (assumption)
modulus_of_elasticity = polyfit(strain_elastic(linear_idx), stress_elastic(linear_idx), 1);
modulus_of_elasticity = modulus_of_elasticity(1); %slope gives modulus of elasticity


offset_yield_strength = 45.6 * 1000; % Convert kpsi to psi
reverse_strain = (offset_yield_strength / modulus_of_elasticity) - 0.002;

interpolated_strain = NaN; 
for i = 1:length(stress_elastic)-1
    if stress_elastic(i) <= offset_yield_strength && stress_elastic(i+1) >= offset_yield_strength
        %interpolation
        x1 = strain_elastic(i); x2 = strain_elastic(i+1);
        y1 = stress_elastic(i); y2 = stress_elastic(i+1);
        interpolated_strain = x1 + (offset_yield_strength - y1) * (x2 - x1) / (y2 - y1);
        break;
    end
end

%ultimate Tensile Strength
ultimate_tensile_strength = max(stress_plastic);

%percent Reduction in Area
final_area = area(end);
percent_reduction_area = ((initial_area - final_area) / initial_area) * 100;


fprintf('Modulus of Elasticity: %.2f Mpsi\n', modulus_of_elasticity / 1e6);
if ~isnan(interpolated_strain)
    fprintf('0.2%% Offset Yield Strength: %.2f kpsi (Verified at strain %.5f)\n', offset_yield_strength / 1e3, interpolated_strain);
else
    fprintf('0.2%% Offset Yield Strength: %.2f kpsi (Strain not found)\n',offset_yield_strength / 1e3);
end
fprintf('Ultimate Tensile Strength: %.2f kpsi\n', ultimate_tensile_strength / 1e3);
fprintf('Percent Reduction in Area: %.2f%%\n', percent_reduction_area);
