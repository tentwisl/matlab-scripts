function [u, elemForce, reactions, Kglob, Fglob] = direct_solver_tommy(nodeCount, conn, k_e, fixedNodes, loads)
Ne = size(conn,1);
Kglob = zeros(nodeCount); %global k
for e = 1:Ne
    i = conn(e,1); j = conn(e,2);
    ke = k_e(e) * [ 1 -1; -1 1 ];
    Kglob([i j],[i j]) = Kglob([i j],[i j]) + ke;
end

Fglob = loads(:);


prescribed = nan(nodeCount,1);  %boundary condition
prescribed(fixedNodes) = 0;             

free    = find(isnan(prescribed));
fixed   = find(~isnan(prescribed));

Kff = Kglob(free,free);  Kfc = Kglob(free,fixed); %parition
Kcf = Kglob(fixed,free); Kcc = Kglob(fixed,fixed);
Ff = Fglob(free);        Uc = prescribed(fixed);


uf = Kff \ (Ff - Kfc*Uc); %solve
u = zeros(nodeCount,1);
u(free) = uf;  u(fixed) = Uc;


elemForce = zeros(Ne,1); %element forces
for e = 1:Ne
    i = conn(e,1); j = conn(e,2);
    elemForce(e) = k_e(e) * (u(j) - u(i));
end


R = Kglob*u - Fglob;     %reactions
reactions = zeros(nodeCount,1); reactions(fixed) = R(fixed);
end
